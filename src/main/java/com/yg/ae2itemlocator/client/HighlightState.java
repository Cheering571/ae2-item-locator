package com.yg.ae2itemlocator.client;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class HighlightState {

    private static AEKey key;
    private static List<CellRef> refs = List.of();
    private static long lockedAt;

    public static void lock(AEKey lockedKey, List<CellRef> lockedRefs) {
        key = lockedKey;
        refs = List.copyOf(lockedRefs);
        lockedAt = System.currentTimeMillis();
    }

    public static void clear() {
        key = null;
        refs = List.of();
    }

    public static boolean isLocked() {
        return key != null;
    }

    public static boolean hasRefs() {
        return !refs.isEmpty();
    }

    public static boolean isLocked(AEKey other) {
        return key != null && key.equals(other);
    }

    public static List<CellRef> refs() {
        return refs;
    }

    public static AEKey key() {
        return key;
    }

    /**
     * Refreshes the highlighted positions without dropping the lock. An empty list means "the item
     * is currently in no drive at all" — the lock stays so the highlight comes back by itself when
     * the disk is inserted again.
     */
    public static void updateRefs(List<CellRef> newRefs) {
        if (key != null) {
            refs = newRefs == null ? List.of() : List.copyOf(newRefs);
        }
    }

    public static long lockedAt() {
        return lockedAt;
    }

    public static Component name() {
        return key == null ? Component.empty() : key.getDisplayName();
    }

    private HighlightState() {
    }
}
