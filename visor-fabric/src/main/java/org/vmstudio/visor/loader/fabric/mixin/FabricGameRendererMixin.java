package org.vmstudio.visor.loader.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.vmstudio.visor.core.client.render.helpers.RenderPoseHelper;
import org.vmstudio.visor.core.client.render.VRRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(GameRenderer.class)
public class FabricGameRendererMixin {

    @Redirect(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 2
            )
    )
    private void visor$removeMulPoseXRotation(PoseStack poseStack, Quaternionf quaternion) {
        if (VRRenderState.getPhase().isVanilla()) {
            poseStack.mulPose(quaternion);
        }
    }

    @Redirect(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 3
            )
    )
    private void visor$removeMulPoseYRotation(PoseStack poseStack, Quaternionf quaternion) {
        if (VRRenderState.getPhase().isVanilla()) {
            poseStack.mulPose(quaternion);
        } else {
            RenderPoseHelper.applyCameraOrientation(VRRenderState.getRenderPass(), poseStack);
        }
    }
}
