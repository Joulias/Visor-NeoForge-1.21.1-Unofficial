package org.vmstudio.visor.mixin.common.world.entity.projectiles;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.server.player.VRServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Projectile.class)
public abstract class AbstractHurtingProjectileMixin {

    @WrapOperation(
            method = "deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileDeflection;deflect(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)V"))
    private void visor$onDeflectByVRPlayer(ProjectileDeflection deflection,
                                           Projectile projectile,
                                           Entity deflector,
                                           RandomSource random,
                                           Operation<Void> original) {
        if (deflection != ProjectileDeflection.AIM_DEFLECT
                || !(deflector instanceof ServerPlayer player)) {
            original.call(deflection, projectile, deflector, random);
            return;
        }
        VRServerPlayer vrPlayer = VisorAPI.server()
                .getVRPlayer(player);
        if (vrPlayer == null) {
            original.call(deflection, projectile, deflector, random);
            return;
        }

        projectile.setDeltaMovement(vrPlayer.getPoseData()
                .getHmd()
                .getDirectionVec3()
                .normalize());
        projectile.hasImpulse = true;
    }
}
