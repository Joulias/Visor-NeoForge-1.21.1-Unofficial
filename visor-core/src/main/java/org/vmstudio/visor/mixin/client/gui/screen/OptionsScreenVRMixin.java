package org.vmstudio.visor.mixin.client.gui.screen;

import org.vmstudio.visor.core.client.gui.screens.settings.VRSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenVRMixin extends Screen {
    protected OptionsScreenVRMixin(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void visor$addVRSettingsButton(CallbackInfo ci) {
        this.addRenderableWidget(
                Button.builder(Component.translatable("visor.options.main.button"), button -> {
                            Minecraft.getInstance().options.save();
                            Minecraft.getInstance().setScreen(new VRSettingsScreen(this));
                        })
                        .bounds(this.width / 2 - 155, 4, 150, 20)
                        .build()
        );
    }




}
