package org.vmstudio.visor.mixin.client.gui;


import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(WidgetTooltipHolder.class)
public class TooltipMixin {
    @Unique
    private Screen visor$attachedTo;

    @Redirect(
            method = "refreshTooltipForNextRenderPass",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"
            )
    )
    private Screen visor$redirectMinecraftScreen(Minecraft minecraftInstance) {
        if(visor$attachedTo == null){
            if(VisorState.get().isNotActive()){
                visor$attachedTo = Minecraft.getInstance().screen;
                return visor$attachedTo;
            }
            VROverlayScreen overlay = ClientContext.cursorHandler
                    .getFocusedOverlayScreen();


            if(overlay != null){
                visor$attachedTo = overlay;
            }else{
                visor$attachedTo = Minecraft.getInstance().screen;
            }
            return visor$attachedTo;
        }

        return visor$attachedTo;
    }
}
