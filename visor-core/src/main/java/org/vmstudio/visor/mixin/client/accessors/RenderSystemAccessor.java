package org.vmstudio.visor.mixin.client.accessors;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {

    @Accessor(value = "shaderTextures", remap = false)
    static int[] getShaderTextures() {
        return null;
    }

    @Accessor(value = "shaderLightDirections", remap = false)
    static Vector3f[] getShaderLightDirections() {
        return null;
    }
}
