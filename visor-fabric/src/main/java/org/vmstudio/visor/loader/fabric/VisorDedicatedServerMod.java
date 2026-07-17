package org.vmstudio.visor.loader.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.vmstudio.visor.core.common.addon.AddonManagerImpl;

/**
 * Finalizes Visor add-on registration after every mod's common initializer has run.
 */
public final class VisorDedicatedServerMod implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        AddonManagerImpl.register();
    }
}