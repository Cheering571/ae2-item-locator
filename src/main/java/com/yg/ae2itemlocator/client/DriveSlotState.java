package com.yg.ae2itemlocator.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.AEBaseScreen;
import com.yg.ae2itemlocator.net.DriveSlotQueryPayload;
import com.yg.ae2itemlocator.net.DriveSlotResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks which slots of the currently open storage GUI hold the locked item.
 *
 * <p>Asks the server because a client-side menu does not expose the drive block entity (AE2 only
 * stores it when the menu target is a BlockEntity, which is server-only).
 */
public final class DriveSlotState {

    private static final long REFRESH_MS = 250L;

    private static int containerId = -1;
    private static AEKey key;
    private static final Set<Integer> SLOTS = new HashSet<>();
    private static long lastRequest;

    public static void tick(Minecraft minecraft) {
        if (!HighlightState.isLocked()) {
            reset();
            return;
        }
        if (!(minecraft.screen instanceof AEBaseScreen<?> screen)) {
            reset();
            return;
        }

        AEKey locked = HighlightState.key();
        int currentContainer = screen.getMenu().containerId;
        long now = System.currentTimeMillis();

        boolean changed = currentContainer != containerId || !Objects.equals(locked, key);
        if (changed) {
            containerId = currentContainer;
            key = locked;
            SLOTS.clear();
            lastRequest = 0;
        }

        if (changed || now - lastRequest > REFRESH_MS) {
            lastRequest = now;
            PacketDistributor.sendToServer(new DriveSlotQueryPayload(currentContainer, locked));
        }
    }

    public static void accept(DriveSlotResultPayload payload) {
        if (!Objects.equals(payload.key(), key)) {
            return;
        }
        SLOTS.clear();
        for (int slot : payload.slots()) {
            SLOTS.add(slot);
        }
    }

    public static boolean isHighlighted(int slotIndex) {
        return SLOTS.contains(slotIndex);
    }

    public static boolean isEmpty() {
        return SLOTS.isEmpty();
    }

    private static void reset() {
        containerId = -1;
        key = null;
        SLOTS.clear();
    }

    private DriveSlotState() {
    }
}
