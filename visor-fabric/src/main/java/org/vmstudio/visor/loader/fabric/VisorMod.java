package org.vmstudio.visor.loader.fabric;


import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class VisorMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(FabricModLoader.VisorPayload.TYPE, FabricModLoader.VisorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FabricModLoader.VisorPayload.TYPE, FabricModLoader.VisorPayload.STREAM_CODEC);
    }
}
