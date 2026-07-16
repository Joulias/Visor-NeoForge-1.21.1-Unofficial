package org.vmstudio.visor.compatibility.iris.mixin.dh;

import net.irisshaders.iris.compat.dh.IrisLodRenderProgram;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vmstudio.visor.compatibility.ClassDependentMixin;
import org.vmstudio.visor.compatibility.dh.DhCompatHelper;

@Pseudo
@ClassDependentMixin("net.irisshaders.iris.compat.dh.IrisLodRenderProgram")
@Mixin(value = IrisLodRenderProgram.class, remap = false)
public class IrisLodRenderProgramVRMixin {
    @Group(name = "projectionAdjust", min = 1, max = 1)
    @ModifyVariable(
            method = "fillUniformData",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0,
            expect = 0,
            remap = false
    )
    private Matrix4fc visor$useVrEyeFrustum(Matrix4fc projection) {
        return visor$correctProjection(projection);
    }

    @Group(name = "projectionAdjust", min = 1, max = 1)
    @ModifyVariable(
            method = "fillUniformData",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true,
            require = 0,
            expect = 0,
            remap = false
    )
    private Matrix4f visor$useVrEyeFrustumMutable(Matrix4f projection) {
        return (Matrix4f) visor$correctProjection(projection);
    }

    @Unique
    private Matrix4fc visor$correctProjection(Matrix4fc projection) {
        if (!DhCompatHelper.isVrEyeWorldPass()
                || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return projection;
        }

        Matrix4fc vrProjection = CapturedRenderingState.INSTANCE.getGbufferProjection();
        Matrix4f corrected = new Matrix4f(projection);
        corrected.m00(vrProjection.m00());
        corrected.m11(vrProjection.m11());
        corrected.m20(vrProjection.m20());
        corrected.m21(vrProjection.m21());
        return corrected;
    }
}
