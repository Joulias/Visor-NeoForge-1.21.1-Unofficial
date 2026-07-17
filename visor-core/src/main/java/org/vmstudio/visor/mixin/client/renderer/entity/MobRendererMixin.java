package org.vmstudio.visor.mixin.client.renderer.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.player.VRClientPlayers;
import org.vmstudio.visor.core.client.render.VRRenderState;

@Mixin(EntityRenderer.class)
public class MobRendererMixin {
    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"), method = "renderLeash")
    public Vec3 visor$vrRenderLeash(Entity instance, float partialTick, Operation<Vec3> original) {
        if (VRRenderState.getPhase().isNotVRWorld()) {
            return original.call(instance, partialTick);
        }

        if (!(instance instanceof Player player)) {
            return original.call(instance, partialTick);
        }

        var vrPlayer = VRClientPlayers.getPlayer(player);
        if (vrPlayer == null) {
            return original.call(instance, partialTick);
        }

        return new Vec3(
                new Vector3f(
                        vrPlayer.getPoseData(PlayerPoseType.RENDER)
                                .getHand(HandType.MAIN)
                                .getPosition()
                )
        );
    }
}
