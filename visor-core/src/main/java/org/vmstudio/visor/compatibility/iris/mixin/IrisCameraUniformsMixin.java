package org.vmstudio.visor.compatibility.iris.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.render.VRRenderState;
import org.vmstudio.visor.core.client.render.helpers.RenderPoseHelper;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CameraUniforms", remap = false)
public class IrisCameraUniformsMixin {
    @ModifyExpressionValue(
            method = "lambda$addCameraUniforms$0",
            at = @At(value = "CONSTANT", args = "doubleValue=0.05"),
            remap = false
    )
    private static double visor$useVrNearPlane(double original) {
        return VisorState.get().isActive() && VRRenderState.getPhase().isVRWorld() ? 0.02D : original;
    }

    @Inject(method = "getUnshiftedCameraPosition", at = @At("HEAD"), cancellable = true)
    private static void visor$useCurrentVrCameraPosition(CallbackInfoReturnable<Vector3d> cir) {
        if (VisorState.get().isNotActive() || VRRenderState.getPhase().isNotVRWorld()) {
            return;
        }

        VRRenderPass renderPass = VRRenderState.getRenderPass();
        if (renderPass == null || !renderPass.isWorld()) {
            return;
        }

        var renderPose = ClientContext.localPlayer.getPoseData(PlayerPoseType.RENDER);
        var cameraPosition = RenderPoseHelper.getCameraPosition(renderPass, renderPose);
        cir.setReturnValue(new Vector3d(cameraPosition.x(), cameraPosition.y(), cameraPosition.z()));
    }
}
