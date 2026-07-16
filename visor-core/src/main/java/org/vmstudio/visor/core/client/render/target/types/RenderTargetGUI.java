package org.vmstudio.visor.core.client.render.target.types;

import com.mojang.blaze3d.pipeline.RenderTarget;
import lombok.AccessLevel;
import lombok.Getter;
import me.phoenixra.atumvr.api.utils.GLUtils;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorClientImpl;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.render.target.RenderTargetHolder;
import org.vmstudio.visor.core.client.render.target.VRRenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;

@Getter
public class RenderTargetGUI implements RenderTargetHolder {
    private static final long TARGET_VALIDATION_INTERVAL_FRAMES = 30L;

    private RenderTarget target = null;

    private final HashMap<VROverlayScreen, VRRenderTarget> overlayTargets = new HashMap<>();
    @Getter(AccessLevel.NONE)
    private final HashMap<VROverlayScreen, Long> overlayValidationFrames = new HashMap<>();


    private int savedWidth;
    private int savedHeight;
    private boolean init;
    @Override
    public void init(int width, int height) throws Exception {
        target = new VRRenderTarget(
                "GUI",
                width, height,
                true,
                ()-> -1, true, false
        );
        GLUtils.checkGLError("GUI target setup");
        VisorClientImpl.LOGGER.info(target.toString());


        overlayTargets.clear();
        overlayValidationFrames.clear();
        for(VROverlay overlay : ClientContext.overlayManager.getOverlaysRegistry().getSortedComponents()) {
            if(overlay instanceof VROverlayScreen overlayScreen) {
                if(!overlay.isVisible() || !overlay.isEnabled()){
                    overlayTargets.put(overlayScreen, null);
                    overlayScreen.setRenderTarget(null);
                    continue;
                }
                VRRenderTarget renderTarget = createOverlayTarget(overlayScreen);
                overlayTargets.put(overlayScreen, renderTarget);
                overlayScreen.setRenderTarget(renderTarget);
            }
        }

        savedWidth = width;
        savedHeight = height;
        init = true;
    }

    @Override
    public void resize(int width, int height) throws Exception {
        target.resize(
                width, height,
                Minecraft.ON_OSX
        );
        for(var entry : overlayTargets.entrySet()) {
            VRRenderTarget overlayTarget = entry.getValue();
            if(overlayTarget == null) continue;
            var overlay = entry.getKey();
            overlayTarget.resize(
                    overlay.getRequestedWidth(),
                    overlay.getRequestedHeight(),
                    Minecraft.ON_OSX
            );
            overlay.updateSize();
        }
        savedWidth = width;
        savedHeight = height;
    }

    @Override
    public void destroy() {
        if(target != null){
            target.destroyBuffers();
            target = null;
        }
        for(VRRenderTarget target : overlayTargets.values()) {
            if(target==null) continue;
            target.destroyBuffers();
        }
        overlayTargets.clear();
        overlayValidationFrames.clear();

        init = false;
    }

    public void updateOverlayTarget(@NotNull VROverlayScreen overlayScreen){
        VRRenderTarget renderTarget = overlayTargets.get(overlayScreen);
        boolean visible = overlayScreen.isVisible();
        if(renderTarget == null && visible){
            renderTarget = createOverlayTarget(overlayScreen);
            overlayTargets.put(overlayScreen, renderTarget);
            overlayValidationFrames.remove(overlayScreen);
        }else if(renderTarget != null && !visible){
            renderTarget.destroyBuffers();
            overlayTargets.put(overlayScreen, null);
            overlayValidationFrames.remove(overlayScreen);
        }else if(renderTarget != null){
            int neededWidth = overlayScreen.getRequestedWidth();
            int neededHeight = overlayScreen.getRequestedHeight();
            if(neededWidth != renderTarget.width
                    || neededHeight != renderTarget.height){
                renderTarget.destroyBuffers();
                renderTarget.resize(
                        neededWidth,
                        neededHeight,
                        Minecraft.ON_OSX
                );
                overlayScreen.updateSize();
            }
        }
        overlayScreen.setRenderTarget(
                overlayTargets.get(overlayScreen)
        );
    }

    public boolean ensureOverlayTargetValid(@NotNull VROverlayScreen overlayScreen) {
        VRRenderTarget renderTarget = overlayTargets.get(overlayScreen);
        if (renderTarget == null) {
            overlayScreen.setRenderTarget(null);
            return false;
        }

        long currentFrame = VisorState.FRAME_COUNT;
        Long lastValidationFrame = overlayValidationFrames.get(overlayScreen);
        if (lastValidationFrame != null
                && currentFrame - lastValidationFrame < TARGET_VALIDATION_INTERVAL_FRAMES) {
            return true;
        }
        overlayValidationFrames.put(overlayScreen, currentFrame);

        boolean framebufferValid = renderTarget.frameBufferId > 0
                && GL30.glIsFramebuffer(renderTarget.frameBufferId);
        boolean textureValid = renderTarget.getColorTextureId() > 0
                && GL11.glIsTexture(renderTarget.getColorTextureId());
        boolean depthValid = !renderTarget.useDepth
                || renderTarget.getDepthTextureId() > 0
                && GL11.glIsTexture(renderTarget.getDepthTextureId());
        if (!framebufferValid || !textureValid || !depthValid) {
            VisorClientImpl.LOGGER.warn(
                    "Rebuilding invalid VR overlay target '{}': framebufferValid={}, textureValid={}, depthValid={}",
                    overlayScreen.getId(), framebufferValid, textureValid, depthValid
            );
            recreateOverlayTarget(overlayScreen);
        }
        return overlayScreen.getRenderTarget() != null;
    }

    public void recreateOverlayTarget(@NotNull VROverlayScreen overlayScreen) {
        overlayValidationFrames.remove(overlayScreen);
        VRRenderTarget previous = overlayTargets.remove(overlayScreen);
        if (previous != null) {
            previous.destroyBuffers();
        }

        if (!overlayScreen.isVisible() || !overlayScreen.isEnabled()) {
            overlayTargets.put(overlayScreen, null);
            overlayScreen.setRenderTarget(null);
            return;
        }

        VRRenderTarget replacement = createOverlayTarget(overlayScreen);
        overlayTargets.put(overlayScreen, replacement);
        overlayScreen.setRenderTarget(replacement);
    }

    private VRRenderTarget createOverlayTarget(@NotNull VROverlayScreen overlayScreen) {
        VRRenderTarget renderTarget = new VRRenderTarget(
                "Overlay " + overlayScreen.getId(),
                overlayScreen.getRequestedWidth(),
                overlayScreen.getRequestedHeight(),
                true,
                () -> -1,
                true, false
        );
        GLUtils.checkGLError("Overlay " + overlayScreen.getId() + " framebuffer setup");
        return renderTarget;
    }

}
