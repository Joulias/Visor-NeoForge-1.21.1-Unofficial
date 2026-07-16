package org.vmstudio.visor.mixin.client.renderer.entity.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.render.VRRenderState;
import org.vmstudio.visor.core.client.render.helpers.RenderPoseHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin extends EntityRenderer<FishingHook> {

    protected FishingHookRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(at = @At(value = "HEAD"), method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
    cancellable = true)
    private void visor$noRenderOnGameScreen(FishingHook fishingHook,
                                           float f, float g,
                                           PoseStack poseStack,
                                           MultiBufferSource multiBufferSource,
                                           int i,
                                           CallbackInfo ci
    ){
        if(MC.screen != null){
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;getPlayerHandPos(Lnet/minecraft/world/entity/player/Player;FF)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 visor$fishingLineStart(Vec3 original,
                                       @Local(argsOnly = true) FishingHook fishingHook) {
        if(VRRenderState.getPhase().isVanilla()
                || !this.entityRenderDispatcher.options.getCameraType().isFirstPerson()
                || fishingHook.getPlayerOwner() != MC.player){
            return original;
        }
        var renderPose = ClientContext.localPlayer
                .getPoseData(PlayerPoseType.RENDER);

        HandType handType = HandType.OFFHAND;
        if (fishingHook.getPlayerOwner().getMainHandItem().getItem() instanceof FishingRodItem) {
            handType = HandType.MAIN;
        }
        Vector3f handPos = new Vector3f(
                RenderPoseHelper.getHandPosition(handType)
        );

        Vector3f handDir = renderPose
                .getGripHand(handType).getCustomVector(
                        new Vector3f(-0.05f,-0.06f,-1.0f)
                );

        float worldScale = renderPose.getWorldScale();
        Vector3f finalPos = handPos.add(
                        new Vector3f(handDir).mul(
                                0.525f * worldScale
                        )
                );

        return new Vec3(finalPos);
    }

}
