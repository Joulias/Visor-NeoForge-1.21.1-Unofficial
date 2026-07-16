package org.vmstudio.visor.mixin.client.compatibility.journeymap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.ClassDependentMixin;
import org.vmstudio.visor.core.client.VisorState;

/**
 * JourneyMap 6 renders its minimap and waypoint decorations while vanilla
 * chat is open. Those desktop HUD elements do not belong on Visor's floating
 * chat surface, so omit only JourneyMap's Chat-time draw calls in VR.
 */
@Pseudo
@ClassDependentMixin("journeymap.client.event.handlers.HudOverlayHandler")
@Mixin(targets = "journeymap.client.event.handlers.HudOverlayHandler", remap = false)
public class JourneyMapHudOverlayHandlerMixin {
    @Inject(
            method = "onRenderOverlay(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void visor$hideMinimapInVrChat(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (isVrChatOpen()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderWaypointDecos(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void visor$hideWaypointDecorationsInVrChat(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (isVrChatOpen()) {
            ci.cancel();
        }
    }

    private static boolean isVrChatOpen() {
        return VisorState.get().isActive()
                && Minecraft.getInstance().screen instanceof ChatScreen;
    }
}
