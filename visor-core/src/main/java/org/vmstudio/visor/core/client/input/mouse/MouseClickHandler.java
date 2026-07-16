package org.vmstudio.visor.core.client.input.mouse;

import lombok.Setter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.vmstudio.visor.api.client.ClientFeature;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.api.client.gui.overlays.framework.screen.VROverlayScreenInScreen;
import org.vmstudio.visor.api.client.input.InputHelper;
import org.vmstudio.visor.api.client.input.MouseButtonType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import static org.vmstudio.visor.core.client.VisorClientImpl.MC;

public class MouseClickHandler {

    public final static MouseClickHandler LEFT_HANDLER = new MouseClickHandler(MouseButtonType.LEFT);

    public final static MouseClickHandler RIGHT_HANDLER = new MouseClickHandler(MouseButtonType.RIGHT);

    public final static MouseClickHandler MIDDLE_HANDLER = new MouseClickHandler(MouseButtonType.MIDDLE);

    private final MouseButtonType buttonType;

    private boolean mainHandPressed;
    private boolean offhandPressed;

    private boolean mainHandChanged;
    private boolean offhandChanged;

    private VROverlay previousFocus;
    private VROverlay pressedOverlay;
    private AbstractContainerMenu pendingContainerReleaseMenu;
    // Both controllers share this mouse-button handler. Only the controller
    // whose press was accepted may release its captured GUI or world state.
    private HandType pressOwner;
    private boolean wasPressedOverlay;
    private boolean ignoreSingleClick;
    private boolean ignoreSingleRelease;

    private boolean gamePressed;

    // only used by left-click
    private final boolean isLeftClick;
    @Setter
    private boolean forcedMain, forcedOffhand;

    public MouseClickHandler(MouseButtonType buttonType) {
        this.buttonType = buttonType;
        this.isLeftClick = buttonType == MouseButtonType.LEFT;
    }

    public void updateState(@NotNull HandType handType,
                            boolean pressed,
                            boolean changed) {
        switch (handType) {
            case MAIN -> {
                mainHandPressed = pressed;
                mainHandChanged = changed;
            }
            case OFFHAND -> {
                offhandPressed = pressed;
                offhandChanged = changed;
            }
        }
    }

    public void preTick() {
        if (!ClientContext.visor.isFeatureEnabled(ClientFeature.INPUT_MOUSE)) {
            return;
        }

        VROverlay focusedOverlay = ClientContext.cursorHandler.getFocusedOverlay();

        // --- Cleanup Clicks ---
        if (focusedOverlay != null
                && previousFocus == null
                && InputHelper.isMousePressed(buttonType)) {
            InputHelper.releaseMouse(buttonType);
        } else if (focusedOverlay == null
                && pressedOverlay != null
                && wasPressedOverlay) {
            releasePressedOverlayForFocusChange();
        } else if (focusedOverlay != null
                && pressedOverlay != null
                && focusedOverlay != pressedOverlay
                && wasPressedOverlay) {
            releasePressedOverlayForFocusChange();
        }
        previousFocus = focusedOverlay;

        // --- Cursor hand switching (left click only) ---
        if (isLeftClick) {
            processCursorUpdate();
        }
    }

    private void processCursorUpdate() {
        if (!ClientContext.cursorHandler.isAnyHandFocused()) {
            return;
        }

        HandType cursorHand = ClientContext.cursorHandler.getCursorHand();

        boolean offHandClicked = offhandPressed && offhandChanged;
        boolean mainClicked = mainHandPressed && mainHandChanged;
        if (offHandClicked && mainClicked) {
            return;
        }

        if (cursorHand != HandType.OFFHAND
                && offHandClicked
                && !mainHandPressed
                && !mainHandChanged) {

            if (!ClientContext.cursorHandler.isHandFocused(HandType.OFFHAND)) {
                return;
            }
            activateCursorHand(HandType.OFFHAND);
            return;
        }

        if (cursorHand != HandType.MAIN
                && mainClicked
                && !offhandPressed
                && !offhandChanged) {

            if (!ClientContext.cursorHandler.isHandFocused(HandType.MAIN)) {
                return;
            }
            activateCursorHand(HandType.MAIN);
        }
    }

    private void activateCursorHand(@NotNull HandType handType) {
        ClientContext.cursorHandler.setCursorHand(handType);
        ClientContext.cursorHandler.process();

        // Selecting a hand that is already pointing at a GUI should not consume
        // the press which selected it. Only suppress the press if refreshing the
        // cursor lost GUI focus, otherwise it could fall through to world input.
        ignoreSingleClick = !ClientContext.cursorHandler.isCursorHandFocused();
    }

    public void onPress(@NotNull HandType handType) {

        if (!ClientContext.visor.isFeatureEnabled(ClientFeature.INPUT_MOUSE)) {
            return;
        }
        if (pressOwner != null) {
            return;
        }

        if (ClientContext.cursorHandler.isCursorHandFocused()
                || MC.screen != null
                || MC.player == null) {
            var activeHand = ClientContext.cursorHandler.getCursorHand();
            if (handType != activeHand) {
                return;
            }
        }

        ClientContext.inputManager.triggerHapticPulseClick(handType);
        if (isLeftClick && ignoreSingleClick) {
            pressOwner = handType;
            return;
        }
        if (process(handType)) {
            pressOwner = handType;
        }
    }

    public void onRelease(@NotNull HandType handType) {
        if (pressOwner != null && pressOwner != handType) {
            return;
        }

        try {
            restorePendingContainerRelease();

            if (isLeftClick && ignoreSingleClick) {
                ignoreSingleClick = false;
                if (!gamePressed && !wasPressedOverlay) {
                    return;
                }
            }

            if (wasPressedOverlay) {
                VROverlay target = pressedOverlay;
                if (target == null) {
                    target = ClientContext.cursorHandler.getFocusedOverlay();
                }
                if (target != null) {
                    target.mouseReleased(
                            target.getMouseX(),
                            target.getMouseY(),
                            buttonType.getId()
                    );
                    if (target instanceof VROverlayScreen overlayScreen) {
                        overlayScreen.finishDragMouse(buttonType.getId());
                    }
                }
                wasPressedOverlay = false;
                pressedOverlay = null;
            }

            if (gamePressed) {
                InputHelper.releaseMouse(buttonType);
                gamePressed = false;
            }

            ignoreSingleRelease = false;
        } finally {
            pressOwner = null;
        }
    }

    /**
     * Release events may be canceled by a GUI after this handler has already
     * captured a press. Reconcile against the action's final physical state so
     * canceled releases still clear internal mouse ownership and drag state.
     */
    public void reconcileActionState(@NotNull HandType handType,
                                     boolean pressed) {
        if (!pressed && pressOwner == handType) {
            onRelease(handType);
        }
    }

    public void onClear() {
        pendingContainerReleaseMenu = null;
        InputHelper.releaseMouse(buttonType);
        if (pressedOverlay != null && wasPressedOverlay) {
            pressedOverlay.mouseReleased(
                    pressedOverlay.getMouseX(),
                    pressedOverlay.getMouseY(),
                    buttonType.getId()
            );
            if (pressedOverlay instanceof VROverlayScreen overlayScreen) {
                overlayScreen.finishDragMouse(buttonType.getId());
            }
        }
        previousFocus = null;
        pressedOverlay = null;
        pressOwner = null;
        wasPressedOverlay = false;
        ignoreSingleClick = false;
        ignoreSingleRelease = false;
        gamePressed = false;
    }

    private boolean process(@NotNull HandType handType) {
        VROverlay focusedOverlay = ClientContext.cursorHandler.getFocusedOverlay();

        if (focusedOverlay != null) {
            processOverlay(focusedOverlay);
            return true;
        }
        if (MC.screen != null) {
            return processScreen();
        }
        if (MC.player != null) {
            return processGame(handType);
        }
        return false;
    }

    private void processOverlay(VROverlay overlay) {
        pendingContainerReleaseMenu = null;
        overlay.mouseClicked(
                overlay.getMouseX(), overlay.getMouseY(),
                buttonType.getId()
        );
        if (overlay instanceof VROverlayScreen overlayScreen) {
            overlayScreen.startDragMouse(buttonType.getId());
        }
        wasPressedOverlay = true;
        pressedOverlay = overlay;
    }

    private void releasePressedOverlayForFocusChange() {
        VROverlay releasedOverlay = pressedOverlay;
        AbstractContainerMenu releasedMenu = getContainerMenu(releasedOverlay);
        boolean wasManipulatingOverlay = releasedOverlay instanceof VROverlayScreen manipulatedScreen
                && (manipulatedScreen.isBeingDragged() || manipulatedScreen.isBeingResized());

        releasedOverlay.mouseReleased(
                releasedOverlay.getMouseX(),
                releasedOverlay.getMouseY(),
                buttonType.getId()
        );
        if (releasedOverlay instanceof VROverlayScreen overlayScreen) {
            overlayScreen.finishDragMouse(buttonType.getId());
        }

        // Container screens share their menu state even when Visor displays
        // the container and player inventory on separate overlays. Preserve a
        // carried stack until the physical trigger is released over the other
        // half of that same menu.
        if (isLeftClick
                && !wasManipulatingOverlay
                && releasedMenu != null
                && MC.player != null
                && MC.player.containerMenu == releasedMenu
                && !releasedMenu.getCarried().isEmpty()) {
            pendingContainerReleaseMenu = releasedMenu;
        } else {
            pendingContainerReleaseMenu = null;
        }

        wasPressedOverlay = false;
        pressedOverlay = null;
    }

    private void restorePendingContainerRelease() {
        AbstractContainerMenu pendingMenu = pendingContainerReleaseMenu;
        pendingContainerReleaseMenu = null;

        if (pendingMenu == null
                || wasPressedOverlay
                || MC.player == null
                || MC.player.containerMenu != pendingMenu
                || pendingMenu.getCarried().isEmpty()) {
            return;
        }

        VROverlay target = ClientContext.cursorHandler.getFocusedOverlay();
        if (target == null || getContainerMenu(target) != pendingMenu) {
            return;
        }

        pressedOverlay = target;
        wasPressedOverlay = true;
    }

    private AbstractContainerMenu getContainerMenu(VROverlay overlay) {
        if (overlay instanceof VROverlayScreenInScreen<?> screenOverlay
                && screenOverlay.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            return containerScreen.getMenu();
        }
        return null;
    }

    private boolean processScreen() {
        if (!isLeftClick) {
            return false;
        }
        if (MC.level != null) {
            //clicked outside of overlay screen, close the screen
            InputHelper.pressKey(GLFW.GLFW_KEY_ESCAPE);
            InputHelper.releaseKey(GLFW.GLFW_KEY_ESCAPE);
            return true;
        }
        return false;
    }

    private boolean processGame(@NotNull HandType handType) {
        // update active hand if only one hand is pressed
        var activeHand = ClientContext.localPlayer.getActiveHand();
        if (activeHand != handType) {
            if ((mainHandPressed && !offhandPressed)
                    || (!mainHandPressed && offhandPressed)) {
                ClientContext.localPlayer.setActiveHand(handType);
                if(!(forcedMain && mainHandPressed)
                        && !(forcedOffhand && offhandPressed)) {
                    ignoreSingleRelease = true;
                    return true;
                }
            }
            if (ignoreSingleRelease) {
                ignoreSingleRelease = false;
                return false;
            }
        }
        InputHelper.pressMouse(buttonType);
        gamePressed = true;
        return true;
    }
}
