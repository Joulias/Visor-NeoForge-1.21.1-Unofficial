package org.vmstudio.visor.loader.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.ClientFeature;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.gui.VRScreenRenderPolicy;

@EventBusSubscriber(modid = VisorAPI.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeGuiLayerHandler {
    private NeoForgeGuiLayerHandler() {
    }

    /**
     * Screens are rendered after the HUD pass. Letting NeoForge render that
     * pass while a screen is open in VR bakes every registered layer (including
     * modded minimaps and quest/status widgets) into the game-screen overlay
     * behind the menu. Cancel only the HUD pass here; the actual Screen, world,
     * and Visor overlays such as the keyboard are rendered separately.
     */
    @SubscribeEvent
    private static void beforeGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (VisorState.get().isActive()
                && VRScreenRenderPolicy.suppressBackgroundHud(minecraft.screen)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    private static void beforeGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (VisorState.get().isNotActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation layer = event.getName();

        /*
         * Chat messages are part of NeoForge's GUI-layer pass rather than the
         * ChatScreen itself, so that pass cannot be canceled wholesale.  It
         * may also contain arbitrary mod HUD layers, though, which would then
         * be baked into the floating chat screen.  While chat is open, keep
         * only Minecraft's chat layer; the ChatScreen and Visor keyboard are
         * rendered independently.
         */
        if (minecraft.screen instanceof ChatScreen
                && !layer.equals(VanillaGuiLayers.CHAT)) {
            event.setCanceled(true);
            return;
        }

        if (layer.equals(VanillaGuiLayers.SLEEP_OVERLAY)) {
            event.setCanceled(true);
            return;
        }

        if (layer.equals(VanillaGuiLayers.CHAT)) {
            if (!(minecraft.screen instanceof ChatScreen)) {
                event.setCanceled(true);
            }
            return;
        }

        if (isVanillaHudLayer(layer)
                && (minecraft.screen != null
                || ClientContext.visor.isFeatureEnabled(ClientFeature.GUI_DISABLE_HUD))) {
            event.setCanceled(true);
        }
    }

    private static boolean isVanillaHudLayer(ResourceLocation layer) {
        return layer.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layer.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || layer.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layer.equals(VanillaGuiLayers.AIR_LEVEL)
                || layer.equals(VanillaGuiLayers.VEHICLE_HEALTH)
                || layer.equals(VanillaGuiLayers.JUMP_METER)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || layer.equals(VanillaGuiLayers.BOSS_OVERLAY);
    }
}
