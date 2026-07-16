package org.vmstudio.visor.mixin.common.player;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPlayer;
import org.vmstudio.visor.api.server.VRServerSettings;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.common.CommonUtils;
import org.vmstudio.visor.extensions.common.PlayerExtension;

import java.util.Objects;

@Mixin(Player.class)
public abstract class Common_PlayerMixin extends Common_LivingEntityMixin
        implements PlayerExtension {


    @Shadow
    public AbstractContainerMenu containerMenu;

    @Unique
    private HandType visor$swingHand = null;



    @Shadow
    public abstract Abilities getAbilities();
    @Shadow
    public abstract SoundSource getSoundSource();
    @Shadow
    public abstract boolean tryToStartFallFlying();
    @Shadow
    public abstract void remove(Entity.RemovalReason reason);
    @Shadow
    protected abstract float getBlockSpeedFactor();





    @WrapMethod(method = "sweepAttack")
    protected void visor$wrapSweepAttack(Operation<Void> original) {
        original.call();
    }
    @Inject(method = "die", at = @At("TAIL"))
    protected void visor$afterDie(DamageSource damageSource, CallbackInfo ci){

    }


    /* ***************************************** *\
  //--------TWO HANDED VR (OFFHAND SUPPORT)--------\\
    \* ***************************************** */
    @WrapOperation(method = "blockActionRestricted",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack visor$forceHandInBlockRestricted(Player self, Operation<ItemStack> original) {
        ItemStack forced = CommonUtils.FORCED_HAND_ITEM.get();
        return forced != null ? forced : original.call(self);
    }

    // getDestroySpeed → inventory.getDestroySpeed: route the inventory lookup to the forced item
    @WrapOperation(method = "getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float visor$forceInventoryDestroySpeed(Inventory inv, BlockState state, Operation<Float> original) {
        ItemStack forced = CommonUtils.FORCED_HAND_ITEM.get();
        if (forced != null && !forced.isEmpty()) {
            return forced.getDestroySpeed(state);
        }
        return original.call(inv, state);
    }

    @Inject(at = @At("HEAD"), method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            cancellable = true)
    public void visor$hasCorrectToolForDrops(BlockState blockState,
                                             CallbackInfoReturnable<Boolean> ci
    ) {
        if (!VRServerSettings.isTwoHandedVR()) {
            return;
        }
        Player player = (Player) (Object) this;
        VRPlayer vrPlayer = VisorAPI.getVRPlayer(player);
        if (vrPlayer == null) {
            return;
        }

        if (vrPlayer.getActiveHand() == HandType.OFFHAND) {
            ci.setReturnValue(!blockState.requiresCorrectToolForDrops()
                    || vrPlayer.getMcPlayer().getOffhandItem()
                    .isCorrectToolForDrops(blockState));
        }
    }



    /* ***************************************** *\
  //--------BETTER SWINGING + TWO HANDED VR--------\\
    \* ***************************************** */
    @Override @Unique
    public void visor$swingAttack(Entity entity, HandType handType) {
        Player self = (Player)(Object)this;
        if (handType == HandType.MAIN) {
            visor$swingHand = HandType.MAIN;
            self.attack(entity);
            visor$swingHand = null;
            return;
        }
        visor$swingHand = HandType.OFFHAND;
        try {
            self.attack(entity);
        } finally {
            visor$swingHand = null;
        }
    }

    // 1. Use the selected VR hand as the weapon. In 1.21 attack() obtains the
    // weapon through getWeaponItem(), while the later getMainHandItem() call is
    // only used to decide which empty stack must be cleared.
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getWeaponItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack visor$mainHandItem(Player self, Operation<ItemStack> original) {
        VRPlayer vrPlayer = VisorAPI.getVRPlayer(self);
        if (vrPlayer == null) {
            return original.call(self);
        }
        return self.getItemInHand(
                Objects.requireNonNullElseGet(
                        visor$swingHand,
                        vrPlayer::getActiveHand
                ).asInteractionHand()
        );

    }

    // 2. getItemInHand()
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack visor$itemInHand(Player self, InteractionHand hand, Operation<ItemStack> original) {
        VRPlayer vrPlayer = VisorAPI.getVRPlayer(self);
        if (vrPlayer == null) {
            return original.call(self, hand);
        }
        return original.call(
                self,
                Objects.requireNonNullElseGet(
                        visor$swingHand,
                        vrPlayer::getActiveHand
                ).asInteractionHand()
        );

    }

    // 3. ATTACK_DAMAGE attribute for offhand
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double visor$attackDamage(Player self, Holder<Attribute> attribute, Operation<Double> original) {
        VRPlayer vrPlayer = VisorAPI.getVRPlayer(self);
        if (vrPlayer == null) {
            return original.call(self, attribute);
        }
        if (visor$isUsingOffhand(vrPlayer)) {
            return visor$withOffhandAttributes(() -> original.call(self, attribute));
        }
        return original.call(self, attribute);
    }

    // 4. 1.21 calculates knockback from both the attack-knockback attribute
    // and the weapon's enchantment effects. Supply the offhand item to that
    // calculation instead of allowing LivingEntity#getKnockback to read the
    // real main hand again.
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F"))
    private float visor$knockback(Player self, Entity target, DamageSource source,
                                  Operation<Float> original) {
        VRPlayer vrPlayer = VisorAPI.getVRPlayer(self);
        if (vrPlayer == null) {
            return original.call(self, target, source);
        }
        if (visor$isUsingOffhand(vrPlayer)) {
            return visor$withOffhandAttributes(() -> {
                float knockback = (float) self.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
                if (self.level() instanceof ServerLevel serverLevel) {
                    return EnchantmentHelper.modifyKnockback(
                            serverLevel, self.getOffhandItem(), target, source, knockback
                    );
                }
                return knockback;
            });
        }
        return original.call(self, target, source);
    }

    @Unique
    private boolean visor$isUsingOffhand(VRPlayer vrPlayer) {
        return Objects.requireNonNullElseGet(visor$swingHand, vrPlayer::getActiveHand)
                == HandType.OFFHAND;
    }

    @Unique
    private <T> T visor$withOffhandAttributes(java.util.function.Supplier<T> action) {
        Player self = (Player)(Object)this;
        ItemStack main = self.getMainHandItem();
        ItemStack off  = self.getOffhandItem();
        Multimap<Holder<Attribute>, AttributeModifier> mainModifiers = visor$getMainhandModifiers(main);
        Multimap<Holder<Attribute>, AttributeModifier> offModifiers = visor$getMainhandModifiers(off);

        // Strip mainhand modifiers, apply offhand modifiers as if it were mainhand
        if (!main.isEmpty()) {
            self.getAttributes().removeAttributeModifiers(mainModifiers);
        }
        if (!off.isEmpty()) {
            self.getAttributes().addTransientAttributeModifiers(offModifiers);
        }

        try {
            return action.get();
        } finally {
            // Always restore, even if action.get() threw
            if (!off.isEmpty()) {
                self.getAttributes().removeAttributeModifiers(offModifiers);
            }
            if (!main.isEmpty()) {
                self.getAttributes().addTransientAttributeModifiers(mainModifiers);
            }
        }
    }

    @Unique
    private Multimap<Holder<Attribute>, AttributeModifier> visor$getMainhandModifiers(ItemStack stack) {
        ImmutableMultimap.Builder<Holder<Attribute>, AttributeModifier> builder = ImmutableMultimap.builder();
        stack.forEachModifier(EquipmentSlot.MAINHAND,
                (attribute, modifier) -> builder.put(attribute, modifier));
        return builder.build();
    }

}
