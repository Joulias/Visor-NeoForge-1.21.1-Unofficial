package org.vmstudio.visor.core.client.gui;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Shared policy for deciding when gameplay HUD content should not be drawn
 * behind an open screen in VR.
 *
 * <p>Chat is deliberately exempt because its messages are supplied by the
 * normal GUI render pass. The screen itself is rendered separately from that
 * pass, so suppressing the background HUD does not suppress the menu.</p>
 */
public final class VRScreenRenderPolicy {
    private VRScreenRenderPolicy() {
    }

    public static boolean suppressBackgroundHud(@Nullable Screen screen) {
        return screen != null && !(screen instanceof ChatScreen);
    }
}
