package org.vmstudio.visor.api.client.gui.helpers;

import com.mojang.blaze3d.platform.NativeImage;
import me.phoenixra.atumvr.api.misc.color.AtumColor;
import org.vmstudio.visor.api.client.gui.GuiTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class TexturesHelper {
    private TexturesHelper() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }

    private static final Map<AtumColor, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<AtumColor, GuiTexture> CACHE_GUI = new ConcurrentHashMap<>();

    private static final AtumColor WHITE_COLOR = AtumColor.WHITE;
    private static final AtumColor BLACK_COLOR = AtumColor.BLACK;



    public static ResourceLocation getWhiteTexture() {
        return getColorTexture(WHITE_COLOR);
    }

    public static ResourceLocation getBlackTexture() {
        return getColorTexture(BLACK_COLOR);
    }


    public static ResourceLocation getColorTexture(@NotNull AtumColor color) {
        return CACHE.computeIfAbsent(color, TexturesHelper::createAndRegister);
    }

    public static GuiTexture getColorGuiTexture(@NotNull AtumColor color) {
        return CACHE_GUI.computeIfAbsent(color,
                SolidColorGuiTexture::new
        );
    }

    /**
     * Re-registers generated color textures at their existing resource paths
     * so callers that cached a path remain valid after an OpenGL recovery.
     */
    public static void reloadColorTextureCache() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        CACHE.forEach((color, location) -> {
            textureManager.release(location);
            textureManager.register(location, createTexture(color));
        });
    }

    private static ResourceLocation createAndRegister(@NotNull AtumColor color) {
        String name = String.format("visor_%02x%02x%02x%02x",
                color.getRedInt(), color.getGreenInt(), color.getBlueInt(), color.getAlphaInt());
        return Minecraft.getInstance().getTextureManager().register(
                name, createTexture(color)
        );
    }

    private static DynamicTexture createTexture(@NotNull AtumColor color) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 1, 1, true);
        img.setPixelRGBA(0, 0, color.asInt());

        return new DynamicTexture(img);
    }

    /**
     * Solid GUI regions do not need to sample a GPU texture. Drawing them
     * directly also keeps cached option widgets valid across resource and
     * render-target rebuilds. A valid backing path is retained for API
     * compatibility with callers that inspect the resource location.
     */
    private static final class SolidColorGuiTexture extends GuiTexture {
        private final AtumColor sourceColor;
        private final int color;

        private SolidColorGuiTexture(@NotNull AtumColor color) {
            super(getColorTexture(color));
            this.sourceColor = color;
            this.color = color.asInt();
        }

        @Override
        public @NotNull ResourceLocation getResourceLocation() {
            return getColorTexture(sourceColor);
        }

        @Override
        public void blit(@NotNull GuiGraphics gui,
                         int xPos, int yPos,
                         int targetWidth, int targetHeight) {
            gui.fill(
                    xPos, yPos,
                    xPos + targetWidth, yPos + targetHeight,
                    color
            );
        }
    }

}
