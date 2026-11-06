package com.yg.ae2itemlocator.client;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.net.DiskQueryPayload;
import com.yg.ae2itemlocator.net.DiskResultPayload;
import com.yg.ae2itemlocator.net.Network;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for "which drives hold this key", keyed by terminal container id.
 * Never blocks: a miss schedules one request and lets the tooltip render an "indexing" line.
 *
 * <p>Answers are revalidated on a TTL. This matters: a "not in any drive" answer is wrong the
 * moment the player inserts a cell, and without a TTL the client would keep showing it for the
 * whole terminal session (which is exactly the bug where a newly placed cell in another dimension
 * never appeared until the terminal was reopened).
 */
public final class ClientIndexState {

    private static final int MAX_CACHE = 512;
    private static final long REPEAT_REQUEST_MS = 350L;
    private static final long HOVER_VALID_MS = 250L;

    /** How long a "found some drives" answer is trusted before revalidating. */
    private static final long TTL_HIT_MS = 8_000L;
    /** How long a "not stored anywhere" answer is trusted. Short: that is the stale-prone case. */
    private static final long TTL_MISS_MS = 1_500L;
    /** While the player has a highlight locked we poll fast and ask for a targeted scan. */
    private static final long TTL_LOCKED_MS = 250L;

    private record Entry(DiskResultPayload payload, long at) {
    }

    private static final Map<AEKey, Entry> CACHE = new HashMap<>();
    private static final Map<AEKey, Long> PENDING = new HashMap<>();

    private static int containerId = -1;
    private static AEKey hovered;
    private static long hoveredAt;
    private static volatile boolean serverReady;

    public static void markServerReady() {
        serverReady = true;
    }

    public static boolean serverReady() {
        return serverReady;
    }

    public static void noteHover(AEKey key, int currentContainerId) {
        if (currentContainerId != containerId) {
            containerId = currentContainerId;
            CACHE.clear();
            PENDING.clear();
        }
        hovered = key;
        hoveredAt = System.currentTimeMillis();
    }

    public static void clearHover() {
        hovered = null;
    }

    public static AEKey hoveredKey() {
        if (hovered == null) {
            return null;
        }
        if (System.currentTimeMillis() - hoveredAt > HOVER_VALID_MS) {
            return null;
        }
        return hovered;
    }

    /** Last known answer, regardless of age. Used by the keybind so it can act on what is shown. */
    public static DiskResultPayload cached(AEKey key) {
        Entry entry = key == null ? null : CACHE.get(key);
        return entry == null ? null : entry.payload();
    }

    /**
     * @return the best known answer (possibly stale, possibly {@code null} while the very first
     *         request is in flight). Schedules a refresh when the cached answer is too old.
     */
    public static DiskResultPayload get(AEKey key) {
        long now = System.currentTimeMillis();
        Entry entry = CACHE.get(key);
        boolean locked = HighlightState.isLocked(key);

        boolean needRequest;
        if (entry == null) {
            needRequest = true;
        } else if (entry.payload().state() == DiskResultPayload.BUILDING) {
            needRequest = true;
        } else {
            long ttl;
            if (locked) {
                ttl = TTL_LOCKED_MS;
            } else {
                ttl = entry.payload().refs().isEmpty() ? TTL_MISS_MS : TTL_HIT_MS;
            }
            needRequest = now - entry.at() > ttl;
        }

        if (needRequest) {
            request(key, now, locked);
        }

        return entry == null ? null : entry.payload();
    }

    private static void request(AEKey key, long now, boolean force) {
        Long last = PENDING.get(key);
        if (last != null && now - last < REPEAT_REQUEST_MS) {
            return;
        }
        PENDING.put(key, now);
        Network.toServer(new DiskQueryPayload(containerId, key, force));
    }

    private static long lastBackgroundPoll;

    /**
     * Keeps a locked highlight alive while no terminal is open.
     *
     * <p>Tooltip hover is not a reliable clock: once the player closes the terminal and walks to the
     * drive, nothing would ever refresh the highlight. This is called every client tick and polls
     * the server with no container id; the server falls back to the network the player last used.
     */
    public static void pollLocked() {
        AEKey key = HighlightState.key();
        if (key == null) {
            return;
        }
        // While a terminal is open the tooltip path already keeps this fresh; polling here too
        // would double the number of server scans for no benefit.
        if (net.minecraft.client.Minecraft.getInstance().screen
                instanceof appeng.client.gui.me.common.MEStorageScreen<?>) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackgroundPoll < TTL_LOCKED_MS) {
            return;
        }
        lastBackgroundPoll = now;
        Network.toServer(new DiskQueryPayload(-1, key, true));
    }

    public static void accept(DiskResultPayload payload) {
        if (CACHE.size() > MAX_CACHE) {
            CACHE.clear();
            PENDING.clear();
        }
        CACHE.put(payload.key(), new Entry(payload, System.currentTimeMillis()));
        if (payload.state() != DiskResultPayload.BUILDING) {
            PENDING.remove(payload.key());
        }

        // Keep a locked highlight following the disk when it is moved to another drive.
        if (payload.state() == DiskResultPayload.READY && HighlightState.isLocked(payload.key())) {
            HighlightState.updateRefs(payload.refs());
        }
    }

    private ClientIndexState() {
    }
}
