package org.vmstudio.visor.loader.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.vmstudio.visor.core.common.addon.AddonManagerImpl;

/**
 * Finalizes Visor add-on registration after every mod's common initializer has run.
 */
public final class VisorClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AddonManagerImpl.register();
    }
}