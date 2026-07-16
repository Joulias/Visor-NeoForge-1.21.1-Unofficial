package org.vmstudio.visor.mixin.client.gui;

import org.vmstudio.visor.api.client.ClientFeature;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.extensions.client.GuiExtension;
import org.vmstudio.visor.core.client.settings.VRClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Gui.class)
public abstract class GuiMixin implements GuiExtension {

    @Final
    @Shadow
    private Minecraft minecraft;

    /* ********************************** *\
  //--------DISABLE VANILLA OVERLAYS--------\\
    \* ********************************** */
    @Inject(at = @At("HEAD"), method = "renderItemHotbar", cancellable = true)
    public void visor$noVanillaHotbar(CallbackInfo ci) {
        if(VisorState.get().isNotActive()
                || (minecraft.screen == null
                && !VRClientSettings.isHudDisableHotBar()
                && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderPlayerHealth", cancellable = true)
    public void visor$noVanillaPlayerHealth(CallbackInfo ci) {
        if(VisorState.get().isNotActive() || (minecraft.screen == null
                && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderVehicleHealth", cancellable = true)
    public void visor$noVanillaVehicleHealth(CallbackInfo ci) {
        if(VisorState.get().isNotActive() || (minecraft.screen == null
                && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderJumpMeter", cancellable = true)
    public void visor$noVanillaJumpMeter(CallbackInfo ci) {
        if(VisorState.get().isNotActive() || (minecraft.screen == null
                && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderExperienceBar", cancellable = true)
    public void visor$noVanillaExperienceBar(CallbackInfo ci) {
        if(VisorState.get().isNotActive() || (minecraft.screen == null
                && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderChat", cancellable = true)
    public void visor$noVanillaGuiChat(CallbackInfo ci) {
        if (VisorState.get().isActive() && !(minecraft.screen instanceof ChatScreen)) {
            ci.cancel();
        }
    }


    @Inject(at = @At("HEAD"), method = "renderVignette", cancellable = true)
    public void visor$noVanillaVignette(CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderSpyglassOverlay", cancellable = true)
    public void visor$noVanillaSpyglassOverlay(CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderEffects", cancellable = true)
    public void visor$noVanillaEffects(CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderSelectedItemName", cancellable = true)
    public void visor$noVanillaSelectedItemName(CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }
    @Inject(at = @At("HEAD"), method = "renderSavingIndicator", cancellable = true)
    public void visor$noAutoSaveText(CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    public void visor$noTextureOverlay(GuiGraphics guiGraphics, ResourceLocation resourceLocation, float f, CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    public void visor$noPortalOverlay(GuiGraphics guiGraphics, float f, CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "renderCrosshair", cancellable = true)
    public void visor$noCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;
        ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "renderSleepOverlay", cancellable = true)
    public void visor$noSleepOverlay(CallbackInfo ci) {
        if (VisorState.get().isActive()) {
            ci.cancel();
        }
    }

    /**
     * Boss bars are rendered by a layered-draw callback in 1.21, rather than by
     * {@link Gui#render}. Target the overlay itself so this remains stable.
     */
    @Mixin(BossHealthOverlay.class)
    public static class BossHealthOverlayMixin {
        @Inject(at = @At("HEAD"), method = "render", cancellable = true)
        private void visor$noVanillaGuiBossHealth(CallbackInfo ci) {
            Minecraft minecraft = Minecraft.getInstance();
            if (VisorState.get().isActive() && !(minecraft.screen == null
                    && ClientContext.visor.isFeatureDisabled(ClientFeature.GUI_DISABLE_HUD))) {
                ci.cancel();
            }
        }
    }

}
