package org.vmstudio.visor.mixin.client.renderer.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.vmstudio.visor.core.client.render.VRRenderState;
import org.vmstudio.visor.extensions.client.entity.EntityRenderDispatcherExtension;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;cameraOrientation()Lorg/joml/Quaternionf;"), method = "renderNameTag")
    public Quaternionf visor$vrNameTagCameraOrient(EntityRenderDispatcher instance, Operation<Quaternionf> original) {
        if (VRRenderState.getPhase().isNotVRWorld()) {
            return original.call(instance);
        }
        return ((EntityRenderDispatcherExtension) this.entityRenderDispatcher)
                .visor$getCameraOrientationOffset(1.0f,0.5f);
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void visor$hideSpectatedVRNameTag(Entity entity, Component displayName,
                                                PoseStack poseStack, MultiBufferSource buffer,
                                                int packedLight, float partialTick, CallbackInfo ci) {
        if (VRRenderState.isSpectatedVRView(entity)) {
            ci.cancel();
        }
    }
}
