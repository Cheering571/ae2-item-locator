package com.yg.ae2itemlocator.client;

import appeng.api.stacks.AEKey;
import com.mojang.blaze3d.platform.InputConstants;
import com.yg.ae2itemlocator.net.DiskResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Lock semantics: the highlight survives closing the terminal, so you can press the key while
 * hovering, close the GUI and walk to the drive. Press again (with the same key hovered, or in
 * the world) to clear.
 *
 * <p>An item can be locked even while it sits in no drive at all: the lock is kept and the
 * highlight reappears on its own once a disk containing it is inserted.
 */
public final class Keybinds {

    public static final KeyMapping LOCK = new KeyMapping(
            "key.ae2itemlocator.lock",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.ae2itemlocator");

    public static final KeyMapping CONFIG = new KeyMapping(
            "key.ae2itemlocator.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.ae2itemlocator");

    public static void toggle() {
        AEKey hovered = ClientIndexState.hoveredKey();
        if (hovered == null) {
            HighlightState.clear();
            message("hud.ae2itemlocator.nothing");
            return;
        }

        if (HighlightState.isLocked(hovered)) {
            HighlightState.clear();
            message("hud.ae2itemlocator.cleared");
            return;
        }

        DiskResultPayload result = ClientIndexState.cached(hovered);
        HighlightState.lock(hovered, result == null ? java.util.List.of() : result.refs());

        if (result != null && !result.refs().isEmpty()) {
            message("hud.ae2itemlocator.locked", hovered.getDisplayName(), result.refs().size());
        } else {
            message("hud.ae2itemlocator.locked.empty", hovered.getDisplayName());
        }
    }

    private static void message(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.displayClientMessage(
                Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }

    private Keybinds() {
    }
}
