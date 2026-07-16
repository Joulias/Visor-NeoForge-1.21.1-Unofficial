package org.vmstudio.visor.core.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4fStack;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.vmstudio.visor.api.client.gui.VRKeyboardAccessor;
import org.vmstudio.visor.api.client.gui.VROverlayManager;
import org.vmstudio.visor.api.client.gui.OverlayConfigAccessor;
import org.vmstudio.visor.api.client.gui.helpers.TexturesHelper;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.gui.overlays.options.OverlayOptionGroup;
import org.vmstudio.visor.api.client.gui.overlays.options.OptionsScreen;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayFrameBuffer;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorClientImpl;
import org.vmstudio.visor.core.client.gui.overlays.builtin.VROverlayGameScreen;
import org.vmstudio.visor.core.client.gui.overlays.builtin.keyboard.VROverlayKeyboard;
import org.vmstudio.visor.core.client.gui.registry.VROverlayRegistry;
import org.vmstudio.visor.core.client.gui.registry.VROverlayTemplateRegistry;
import org.vmstudio.visor.core.client.gui.screens.VRPauseMenuScreen;
import org.vmstudio.visor.core.client.gui.screens.overlayoptions.*;
import org.vmstudio.visor.extensions.client.render.GameRendererExtension;
import org.vmstudio.visor.core.client.render.VRRenderState;
import org.vmstudio.visor.core.client.render.helpers.RenderGuiHelper;
import org.vmstudio.visor.core.client.render.helpers.RenderPoseHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.vmstudio.visor.api.client.gui.overlays.options.types.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

@Getter
public class VROverlayManagerImpl implements VROverlayManager {

    private final VROverlayRegistry overlaysRegistry = new VROverlayRegistry();
    private final VROverlayTemplateRegistry overlayTemplatesRegistry = new VROverlayTemplateRegistry();


    @Setter
    private VRKeyboardAccessor keyboardAccessor;


    private final List<VROverlay> preparedOverlays = new ArrayList<>();

    private final List<VROverlay> preparedDepthOverlays = new ArrayList<>();
    private final List<VROverlay> preparedHudOverlays = new ArrayList<>();

    private static final long RECOVERABLE_GL_WARNING_INTERVAL_MS = 5_000L;
    @Getter(AccessLevel.NONE)
    private final Map<String, Long> recoverableGlWarningTimes = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private long lastColorTextureCacheReset;
    @Getter(AccessLevel.NONE)
    private final Set<String> activeGlRecoveries = new HashSet<>();

    public void tick(){
        for(VROverlay overlay : overlaysRegistry.getSortedComponents()){
            if(!overlay.isEnabled()) continue;
            overlay.tick();
        }


    }

    public void prepareOverlaysAndCursor(float partialTicks){
        preparedOverlays.clear();
        preparedDepthOverlays.clear();
        preparedHudOverlays.clear();

        for (VROverlay overlay : overlaysRegistry.getSortedComponents()) {
            if (!isOverlayAvailableForCurrentScreen(overlay)) continue;
            if(!overlay.isVisible()) continue;
            RenderTarget target = overlay.getRenderTarget();
            if(target == null){
                continue;
            }

            overlay.updatePose(partialTicks);

            //do not render overlay if out of view distance
            if(!overlay.isInViewDistance()){
                continue;
            }

            //ready to be rendered
            preparedOverlays.add(overlay);

            // Split into depth and HUD layer lists
            if (overlay.isHudLayer()) {
                preparedHudOverlays.add(overlay);
            } else {
                preparedDepthOverlays.add(overlay);
            }
        }
        ClientContext.cursorHandler.process();
    }

    /**
     * Keeps menus with dedicated Visor surfaces exclusive. Gameplay and addon
     * overlays retain their enabled/visible state so they can resume as soon
     * as the menu closes; they are excluded only from this frame's rendering
     * and input admission.
     */
    public boolean isOverlayAvailableForCurrentScreen(@NotNull VROverlay overlay) {
        if (!(MC.screen instanceof VRPauseMenuScreen)
                && !(MC.screen instanceof PauseScreen)
                && !(MC.screen instanceof ChatScreen)) {
            return true;
        }
        return overlay instanceof VROverlayGameScreen
                || overlay instanceof VROverlayKeyboard;
    }

    public void renderOverlayTextures(ProfilerFiller profiler,
                                      GuiGraphics guiGraphics,
                                      float partialTicks) {
        if(preparedOverlays.isEmpty()){
            return;
        }

        RenderTarget previousMainTarget = MC.mainRenderTarget;
        Matrix4f projection = new Matrix4f();
        int prevOverlayWidth = -1;
        int prevOverlayHeight = -1;
        Matrix4fStack posestack = RenderSystem.getModelViewStack();
        boolean projectionBackedUp = false;
        boolean modelViewPushed = false;

        try {
            // Do not attribute an error left by an earlier render stage to the
            // first overlay that happens to drain it.
            drainOverlayGlErrors(null, "before overlay rendering");

            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;

            posestack.pushMatrix();
            modelViewPushed = true;
            posestack.identity();
            posestack.translate(0.0F, 0.0F, -11000.0F);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE
            );

            for(var overlay : preparedOverlays){
                profiler.push("VROverlay Texture: " + overlay.getId());
                try {
                    if (overlay instanceof VROverlayScreen overlayScreen
                            && !ClientContext.renderer.ensureOverlayTargetValid(overlayScreen)) {
                        continue;
                    }

                    RenderTarget target = overlay.getRenderTarget();
                    if(target == null){
                        throw new RuntimeException(
                                "Tried to render overlay quad with null renderTarget: " + overlay.getId()
                        );
                    }

                    if(overlay instanceof VROverlayScreen overlayScreen) {
                        MC.mainRenderTarget = target;
                        target.clear(Minecraft.ON_OSX);
                        target.bindWrite(true);
                        if (!drainOverlayGlErrors(overlay, "render-target setup")) {
                            continue;
                        }

                        if(prevOverlayWidth != overlayScreen.width
                                || prevOverlayHeight != overlayScreen.height) {
                            projection.setOrtho(
                                    0,
                                    overlayScreen.width, overlayScreen.height,
                                    0,
                                    1000.0F, 21000.0F
                            );
                            RenderSystem.setProjectionMatrix(
                                    projection,
                                    VertexSorting.ORTHOGRAPHIC_Z
                            );
                            prevOverlayWidth = overlayScreen.width;
                            prevOverlayHeight = overlayScreen.height;
                        }

                        overlayScreen.renderWithTooltip(
                                guiGraphics,
                                overlayScreen.getMouseX(),
                                overlayScreen.getMouseY(),
                                partialTicks
                        );
                        guiGraphics.flush();
                    }else if(overlay instanceof VROverlayFrameBuffer overlayFrameBuffer){
                        overlayFrameBuffer.render(partialTicks);
                    }else{
                        throw new RuntimeException(
                                "Tried to render overlay of unsupported abstract class: " + overlay.getId()
                        );
                    }

                    drainOverlayGlErrors(overlay, "overlay draw");
                } finally {
                    profiler.pop();
                }
            }
        } finally {
            if (modelViewPushed) {
                posestack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }

            MC.mainRenderTarget = previousMainTarget;
            if (previousMainTarget != null) {
                previousMainTarget.bindWrite(true);
            }
        }

    }

    /**
     * A third-party screen hook or a stale GUI texture can emit 1282 while an
     * independent overlay is being drawn. Minecraft can recover from that
     * error, so isolate it to the overlay instead of tearing down OpenXR and
     * disconnecting the world. Other OpenGL errors retain the existing fatal
     * behavior.
     */
    private boolean drainOverlayGlErrors(@Nullable VROverlay overlay,
                                         @NotNull String stage) {
        String overlayId = overlay == null ? "<previous render stage>" : overlay.getId();
        String warningKey = overlayId + ':' + stage;
        boolean invalidOperation = false;
        int fatalError = GL11.GL_NO_ERROR;

        for (int i = 0; i < 64; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                break;
            }
            if (error == GL11.GL_INVALID_OPERATION) {
                invalidOperation = true;
            } else if (fatalError == GL11.GL_NO_ERROR) {
                fatalError = error;
            }
        }

        if (fatalError != GL11.GL_NO_ERROR) {
            throw new RuntimeException(
                    stage + " OpenGL Error Code: " + fatalError
            );
        }
        if (!invalidOperation) {
            activeGlRecoveries.remove(warningKey);
            return true;
        }

        // Force subsequent GUI draws to bind their textures again rather than
        // trusting a stale RenderSystem slot cache.
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(1, 0);
        RenderSystem.setShaderTexture(2, 0);

        long now = System.currentTimeMillis();
        long previousWarning = recoverableGlWarningTimes.getOrDefault(warningKey, 0L);
        boolean firstErrorInSeries = activeGlRecoveries.add(warningKey);
        if (firstErrorInSeries) {
            if (now - lastColorTextureCacheReset >= RECOVERABLE_GL_WARNING_INTERVAL_MS) {
                TexturesHelper.reloadColorTextureCache();
                lastColorTextureCacheReset = now;
            }
            if (overlay instanceof VROverlayScreen overlayScreen) {
                ClientContext.renderer.recreateOverlayTarget(overlayScreen);
            }
        }
        if (now - previousWarning >= RECOVERABLE_GL_WARNING_INTERVAL_MS) {
            recoverableGlWarningTimes.put(warningKey, now);
            VisorClientImpl.LOGGER.warn(
                    "Isolated OpenGL 1282 at {} for VR overlay '{}'; reset overlay state and kept the VR session active",
                    stage, overlayId
            );
        }
        return false;
    }



    /**
     * Render only overlays that support depth (GL_LEQUAL).
     * These participate in proper depth testing with VR hands and world geometry.
     */
    public void renderDepthOverlays(float partialTicks,
                                    PoseStack poseStack) {
        if (preparedDepthOverlays.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        try {
            poseStack.setIdentity();
            RenderPoseHelper.applyCameraOrientation(
                    VRRenderState.getRenderPass(),
                    poseStack
            );

            ((GameRendererExtension) MC.gameRenderer).visor$resetProjectionMatrix(partialTicks);
            drainOverlayGlErrors(null, "before depth overlays");

            for (VROverlay overlay : preparedDepthOverlays) {
                if (!overlay.isVisible()) {
                    continue;
                }
                var target = overlay.getRenderTarget();
                if (target == null) {
                    throw new RuntimeException("Tried to render overlay quad with null renderTarget: " + overlay.getId());
                }

                boolean drawDragHandle = overlay.supportsDragging() &&
                        (overlay.isBeingDragged() || overlay.isBeingResized() ||
                                ((ClientContext.cursorHandler.getFocusedOverlay(HandType.MAIN,true) == overlay
                                        || ClientContext.cursorHandler.getFocusedOverlay(HandType.OFFHAND,true) == overlay)));

                RenderGuiHelper.renderOverlayQuad(
                        overlay,
                        poseStack,
                        overlay.getPose().getPosition(),
                        overlay.getPose().getRotation(),
                        false, // depthAlways = false, use GL_LEQUAL
                        overlay.supportsLight(),
                        drawDragHandle,
                        overlay.getPose().getScale()
                );
                drainOverlayGlErrors(overlay, "depth overlay draw");
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Render only HUD overlays (no depth testing — GL_ALWAYS).
     * These render as a top layer, not occluded by world objects
     */
    public void renderHudOverlays(float partialTicks,
                                  PoseStack poseStack) {
        if (preparedHudOverlays.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        try {
            poseStack.setIdentity();
            RenderPoseHelper.applyCameraOrientation(
                    VRRenderState.getRenderPass(),
                    poseStack
            );

            ((GameRendererExtension) MC.gameRenderer).visor$resetProjectionMatrix(partialTicks);
            drainOverlayGlErrors(null, "before hud overlays");

            for (VROverlay overlay : preparedHudOverlays) {
                if (!overlay.isVisible()) {
                    continue;
                }
                var target = overlay.getRenderTarget();
                if (target == null) {
                    throw new RuntimeException("Tried to render overlay quad with null renderTarget: " + overlay.getId());
                }

                boolean drawDragHandle = overlay.supportsDragging() &&
                        (overlay.isBeingDragged() || overlay.isBeingResized() ||
                                ((ClientContext.cursorHandler.getFocusedOverlay(HandType.MAIN,true) == overlay
                                        || ClientContext.cursorHandler.getFocusedOverlay(HandType.OFFHAND,true) == overlay)));
                RenderGuiHelper.renderOverlayQuad(
                        overlay,
                        poseStack,
                        overlay.getPose().getPosition(),
                        overlay.getPose().getRotation(),
                        true, // depthAlways = true, use GL_ALWAYS
                        overlay.supportsLight(),
                        drawDragHandle,
                        overlay.getPose().getScale()
                );

                drainOverlayGlErrors(overlay, "HUD overlay draw");
            }
        } finally {
            poseStack.popPose();
        }
    }


    @Override
    public VROverlay getOverlay(@NotNull String id) {
        return overlaysRegistry.getComponent(id);
    }


    @Override
    public @NotNull OverlayConfigAccessor getOverlayConfigAccessor() {
        return ClientContext.settingsManager.getOverlayConfigsAccessor();
    }

    @Override
    public @NotNull OptionsScreen<?> getOptionsScreenFor(@NotNull OverlayOptionGroup<?> category) {
        if(category instanceof OverlayOptionsMisc type){
            return new OptionsScreenMisc(type);
        }
        else if(category instanceof OverlayOptionsPose type){
            return new OptionsScreenPose(type);
        }
        else if(category instanceof OverlayOptionsIdentity type){
            return new OptionsScreenIdentity(type);
        }
        else if(category instanceof OverlayOptionsGeneral type){
            return new OptionsScreenGeneral(type);
        }
        else if(category instanceof OverlayOptionsScreenRegion type){
            return new OptionsScreenRegion(type);
        }else if(category instanceof OverlayOptionsVisibility type){
            return new OptionsScreenVisibility(type);
        }
        return null;
    }
}
