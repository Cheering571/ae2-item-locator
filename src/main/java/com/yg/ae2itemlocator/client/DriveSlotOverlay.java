package com.yg.ae2itemlocator.client;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;
import appeng.api.storage.StorageCells;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Draws a border around the storage slot that holds the locked item.
 *
 * <p>Called from a mixin at the tail of {@code AEBaseScreen#renderSlot}, so the border shares the
 * exact coordinate space and pose AE2 used for the item itself.
 *
 * <p>Slots are recognised by content + slot index rather than by {@code SlotSemantics}: ExtendedAE's
 * 20-slot drive registers its right-hand column under its own custom semantic
 * ({@code ExSemantics.EX_1}), so a semantic whitelist silently misses half the drive.
 */
public final class DriveSlotOverlay {

    public static void renderSlot(GuiGraphics graphics, AEBaseScreen<?> screen, Slot slot) {
        if (!HighlightState.isLocked() || DriveSlotState.isEmpty()) {
            return;
        }

        var menu = screen.getMenu();
        var semantic = menu.getSlotSemantic(slot);

        // Never touch the player's own inventory or the upgrade slots; their container indices
        // overlap with the drive's slot numbering.
        if (semantic == SlotSemantics.PLAYER_INVENTORY
                || semantic == SlotSemantics.PLAYER_HOTBAR
                || semantic == SlotSemantics.UPGRADE) {
            return;
        }

        // A highlighted slot must actually hold a storage cell.
        var stack = slot.getItem();
        if (stack.isEmpty() || !StorageCells.isCellHandled(stack)) {
            return;
        }

        // slot.getContainerSlot() is the index inside the drive's own inventory (AE2 passes it
        // straight to Slot#<init>), which is exactly what the server reports.
        if (!DriveSlotState.isHighlighted(slot.getContainerSlot())) {
            return;
        }

        int x = slot.x - 1;
        int y = slot.y - 1;
        int right = x + 18;
        int bottom = y + 18;

        int border = ClientConfig.slotColor();
        int fill = (border & 0xFFFFFF) | 0x33000000;

        graphics.fill(x, y, right, bottom, fill);

        graphics.fill(x, y, right, y + 1, border);            // top
        graphics.fill(x, bottom - 1, right, bottom, border);  // bottom
        graphics.fill(x, y, x + 1, bottom, border);           // left
        graphics.fill(right - 1, y, right, bottom, border);   // right
    }

    private DriveSlotOverlay() {
    }
}
