package org.vmstudio.visor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Loader access used during early mixin initialization. This interface must
 * not reference Minecraft classes or the full runtime {@code ModLoader} API.
 */
public interface MixinModLoader {
    String MOD_ID = "visor";
    String MOD_NAME = "Visor";

    boolean isModLoaded(@NotNull String id);

    default boolean isSodiumLoaded() {
        return isModLoaded("sodium")
                || isModLoaded("rubidium")
                || isModLoaded("embeddium");
    }

    @NotNull
    LoaderType getType();

    static MixinModLoader get() {
        return Instance.get();
    }

    enum LoaderType {
        NEOFORGE,
        FABRIC
    }

    @ApiStatus.Internal
    final class Instance {
        private static MixinModLoader api;

        private Instance() {
            throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
        }

        static MixinModLoader get() {
            if (api != null) {
                return api;
            }

            try {
                Class<?> clazz = Class.forName("org.vmstudio.visor.loader.forge.ForgeMixinModLoader");
                api = (MixinModLoader) clazz.getConstructor().newInstance();
            } catch (Exception ignored) {
            }

            if (api == null) {
                try {
                    Class<?> clazz = Class.forName("org.vmstudio.visor.loader.fabric.FabricMixinModLoader");
                    api = (MixinModLoader) clazz.getConstructor().newInstance();
                } catch (Exception ignored) {
                }
            }

            if (api == null) {
                throw new RuntimeException("SUPPORTED MIXIN MOD LOADER FOR VISOR NOT FOUND!");
            }
            return api;
        }
    }
}
