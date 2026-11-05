package com.yg.ae2itemlocator.server;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import com.yg.ae2itemlocator.Config;
import com.yg.ae2itemlocator.common.CellRef;
import com.yg.ae2itemlocator.net.DiskResultPayload;
import com.yg.ae2itemlocator.net.DriveSlotResultPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validates a client query, then answers from a fresh targeted scan.
 */
public final class QueryService {

    private static final long MIN_INTERVAL_MS = 40L;

    /**
     * How long a player's last terminal network is remembered. This is what lets a locked
     * highlight keep tracking after the terminal is closed: there is no open menu to identify the
     * network anymore, so the last one the player queried is reused.
     */
    private static final long REMEMBER_MS = 10 * 60 * 1000L;

    private record Remembered(IGridNode node, long at) {
    }

    private static final Map<UUID, Long> LAST_QUERY = new HashMap<>();
    private static final Map<UUID, Remembered> REMEMBERED = new HashMap<>();

    public static void handle(ServerPlayer player, int containerId, AEKey key, boolean precise) {
        if (key == null || !Config.ALLOW_QUERY.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        IGridNode node = null;

        boolean terminalOpen = containerId >= 0
                && player.containerMenu.containerId == containerId
                && player.containerMenu instanceof MEStorageMenu;

        if (terminalOpen) {
            node = ((MEStorageMenu) player.containerMenu).getGridNode();
            if (node != null && node.isActive()) {
                REMEMBERED.put(player.getUUID(), new Remembered(node, now));
            }
        } else if (precise) {
            Remembered remembered = REMEMBERED.get(player.getUUID());
            if (remembered != null && now - remembered.at() < REMEMBER_MS) {
                node = remembered.node;
            }
        } else {
            return;
        }

        Long previous = LAST_QUERY.get(player.getUUID());
        if (previous != null && now - previous < MIN_INTERVAL_MS) {
            return;
        }
        LAST_QUERY.put(player.getUUID(), now);

        IGrid grid = node != null && node.isActive() ? node.getGrid() : null;
        if (grid == null) {
            send(player, key, DiskResultPayload.NONE, 0, List.of(), "none");
            return;
        }

        // Locked highlight: answer from a fresh targeted scan of just this key. It is cheap (one
        // pass over the drives, reusing a single KeyCounter) and always current, which is what
        // makes the highlight appear / disappear / move without a perceptible delay.
        if (precise) {
            send(player, key, DiskResultPayload.READY, 100, IndexManager.scanKey(grid, key), "precise");
            return;
        }

        GridIndex index = IndexManager.get(grid, false);
        if (index.state() == GridIndex.State.READY) {
            send(player, key, DiskResultPayload.READY, 100, index.get(key), "index");
            return;
        }

        // Mid-rebuild: answer from the previous snapshot when it knows this key, otherwise let the
        // client keep polling so it eventually receives the fresh answer.
        List<CellRef> stale = index.get(key);
        if (!stale.isEmpty()) {
            send(player, key, DiskResultPayload.READY, 100, stale, "index-stale");
        } else {
            send(player, key, DiskResultPayload.BUILDING, index.progress(), List.of(), "building");
        }
    }

    private static String lastAnswer = "";

    private static void send(ServerPlayer player, AEKey key, byte state, int progress, List<CellRef> refs) {
        PacketDistributor.sendToPlayer(player, new DiskResultPayload(key, state, progress, List.copyOf(refs)));
    }

    /**
     * Same as {@link #send}, but also logs the answer whenever it changes. Both the index path and
     * the precise path go through here, which makes a mismatch such as "the subnet terminal works
     * but the main terminal does not" diagnosable from a single log line.
     */
    private static void send(ServerPlayer player, AEKey key, byte state, int progress, List<CellRef> refs,
            String path) {
        send(player, key, state, progress, refs);

        var summary = new StringBuilder();
        summary.append(key.getId()).append(" path=").append(path)
                .append(" state=").append(state).append(" refs=").append(refs.size());
        for (CellRef ref : refs) {
            summary.append(" (").append(ref.pos().getX()).append(',')
                    .append(ref.pos().getY()).append(',')
                    .append(ref.pos().getZ()).append(") slot ").append(ref.slot())
                    .append(ref.subnet() ? " subnet" : " root");
        }

        String line = summary.toString();
        if (!line.equals(lastAnswer)) {
            lastAnswer = line;
            com.yg.ae2itemlocator.AE2ItemLocator.LOG.info("[ae2-disk-locator] answer {}", line);
        }
    }

    /**
     * Answers "which slots of the storage block I currently have open contain this item".
     *
     * <p>Deliberately generic: any {@link AEBaseMenu} whose target is an {@link IChestOrDrive}
     * qualifies, so vanilla drives, the ME chest and addon drives (ExtendedAE's {@code TileExDrive},
     * which reports more than ten slots) all work without special cases.
     *
     * <p>Reads the drive's own cells directly instead of consulting the index: it is cheaper,
     * always accurate, and it is exactly the question the open GUI is asking.
     */
    public static void handleDriveSlots(ServerPlayer player, int containerId, AEKey key) {
        if (key == null || !Config.ALLOW_QUERY.get()) {
            return;
        }
        if (player.containerMenu.containerId != containerId) {
            return;
        }
        if (!(player.containerMenu instanceof AEBaseMenu menu)) {
            return;
        }
        if (!(menu.getBlockEntity() instanceof IChestOrDrive drive)) {
            // Not a drive GUI: answer with an empty list so the client clears its state.
            PacketDistributor.sendToPlayer(player, new DriveSlotResultPayload(key, new int[0]));
            return;
        }

        KeyCounter counter = new KeyCounter();
        int cellCount = Math.max(0, drive.getCellCount());
        int[] slots = new int[cellCount];
        int found = 0;

        for (int slot = 0; slot < cellCount; slot++) {
            MEStorage inventory;
            try {
                inventory = drive.getCellInventory(slot);
            } catch (Throwable t) {
                continue;
            }
            if (inventory == null) {
                continue;
            }

            counter.clear();
            try {
                inventory.getAvailableStacks(counter);
            } catch (Throwable t) {
                continue;
            }
            if (counter.get(key) > 0) {
                slots[found++] = slot;
            }
        }

        int[] result = new int[found];
        System.arraycopy(slots, 0, result, 0, found);
        PacketDistributor.sendToPlayer(player, new DriveSlotResultPayload(key, result));
    }

    private QueryService() {
    }
}

