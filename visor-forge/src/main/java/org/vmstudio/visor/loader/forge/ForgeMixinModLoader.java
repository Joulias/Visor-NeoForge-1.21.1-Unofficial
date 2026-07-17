package org.vmstudio.visor.loader.forge;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.MixinModLoader;

public class ForgeMixinModLoader implements MixinModLoader {

    @Override
    public boolean isModLoaded(@NotNull String id) {
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        return loadingModList != null && loadingModList.getModFileById(id) != null;
    }

    @Override
    public @NotNull LoaderType getType() {
        return LoaderType.NEOFORGE;
    }
}
