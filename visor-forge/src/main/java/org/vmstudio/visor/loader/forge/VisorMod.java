package org.vmstudio.visor.loader.forge;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.core.common.addon.AddonManagerImpl;

@Mod(VisorAPI.MOD_ID)
public class VisorMod {

    public VisorMod(final IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterPayloadHandlers);
    }

    private void onRegisterPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        AddonManagerImpl.register();
        ForgeModLoader.onRegisterPayloadHandlers(event);
    }


}
