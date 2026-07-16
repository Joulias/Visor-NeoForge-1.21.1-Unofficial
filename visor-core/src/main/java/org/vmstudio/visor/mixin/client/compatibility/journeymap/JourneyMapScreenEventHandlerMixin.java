package org.vmstudio.visor.mixin.client.compatibility.journeymap;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.ClassDependentMixin;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.gui.VRScreenRenderPolicy;

/**
 * JourneyMap can render its minimap from ScreenEvent.Render.Pre when its
 * "allow minimap behind screens" option is enabled. That path is separate
 * from NeoForge's HUD-layer events, so suppress JourneyMap's screen callback
 * whenever the shared VR screen policy suppresses the background HUD.
 */
@Pseudo
@ClassDependentMixin("journeymap.client.event.handlers.ScreenEventHandler")
@Mixin(targets = "journeymap.client.event.handlers.ScreenEventHandler", remap = false)
public class JourneyMapScreenEventHandlerMixin {
    @Inject(method = "onScreenPreRender", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void visor$hideMinimapBehindScreen(
            Screen screen,
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci) {
        if (VisorState.get().isActive()
                && VRScreenRenderPolicy.suppressBackgroundHud(screen)) {
            ci.cancel();
        }
    }
}
