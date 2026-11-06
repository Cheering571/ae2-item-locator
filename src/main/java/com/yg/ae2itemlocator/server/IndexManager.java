package com.yg.ae2itemlocator.server;

import appeng.api.networking.IGrid;
import com.yg.ae2itemlocator.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps at most a handful of per-grid indexes alive and advances them a few cells per tick.
 */
public final class IndexManager {

    private static final int MAX_INDEXES = 8;
    private static final long TICK_BUDGET_NANOS = 3_000_000L; // 3 ms

    /**
     * Even when the network reports a change, never rebuild more often than this. A full rebuild of
     * a big network is cheap in CPU but takes a moment to slice across ticks; this keeps a busy
     * base (items constantly entering/leaving) from restarting the scan forever.
     */
    private static final long MIN_REBUILD_INTERVAL_MS = 1000L;

    /**
     * While the player has a highlight locked, the client asks for a forced refresh once a second
     * so the box follows a disk that was moved to another drive. Rebuilds are cheap (a few ms for
     * a typical network) and are sliced across ticks, so this is affordable.
     */
    private static final long FORCE_REFRESH_MS = 1000L;

    private static final Map<IGrid, GridIndex> INDEXES = new LinkedHashMap<>(16, 0.75f, true);

    public static GridIndex get(IGrid grid) {
        return get(grid, false);
    }

    public static GridIndex get(IGrid grid, boolean force) {
        long now = System.currentTimeMillis();
        long ttl = Config.CACHE_SECONDS.get() * 1000L;

        GridIndex index = INDEXES.get(grid);
        boolean rebuild;

        if (index == null) {
            rebuild = true;
        } else if (index.state() == GridIndex.State.BUILDING) {
            // A scan is already in flight; let it finish instead of restarting it.
            rebuild = false;
        } else {
            // Only probe for changes when someone actually asks, so idle grids never force AE2 to
            // recompute its aggregated inventory.
            index.pollDirty();

            if (index.isDirty() && now - index.builtAt() >= MIN_REBUILD_INTERVAL_MS) {
                rebuild = true;
            } else if (force && now - index.builtAt() >= FORCE_REFRESH_MS) {
                rebuild = true;
            } else {
                // TTL is measured from when the index was built, NOT from the last query:
                // hovering continuously must not keep a stale index alive forever.
                rebuild = ttl > 0 && now - index.builtAt() > ttl;
            }
        }

        if (rebuild) {
            index = new GridIndex(grid, index);
            INDEXES.put(grid, index);
            evictIfNeeded();
        }

        index.touch();
        return index;
    }

    public static void tick() {
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        int maxCells = Config.CELLS_PER_TICK.get();

        // Snapshot: a payload handled between ticks may add an index.
        for (GridIndex index : new ArrayList<>(INDEXES.values())) {
            if (index.state() != GridIndex.State.READY) {
                index.step(maxCells, deadline);
            }
        }
    }

    public static void clear() {
        INDEXES.clear();
    }

    /**
     * One-shot scan for a single key across every drive on the grid.
     *
     * <p>Reuses one {@link KeyCounter} so a poll costs no allocation per cell, which makes it
     * affordable to run a few times per second while a highlight is locked.
     */
    private static String lastScanSummary = "";

    public static java.util.List<com.yg.ae2itemlocator.common.CellRef> scanKey(IGrid grid,
            appeng.api.stacks.AEKey key) {
        var result = new java.util.ArrayList<com.yg.ae2itemlocator.common.CellRef>(2);
        var counter = new appeng.api.stacks.KeyCounter();
        var detail = new StringBuilder();
        var seen = new java.util.HashSet<Long>();
        int networks = 0;
        int driveTotal = 0;

        // Walk the network plus any subnetwork bridged through a storage bus / adjacent grid.
        for (IGrid scanned : GridIndex.collectGrids(grid)) {
            boolean subnet = scanned != grid;
            networks++;
            var drives = GridIndex.findDrives(scanned);
            driveTotal += drives.size();
            if (networks > 1) {
                detail.append("[net ").append(networks).append(subnet ? " subnet" : " root")
                        .append(' ').append(drives.size()).append(" drives] ");
            }
            for (var drive : drives) {
                scanDrive(new GridIndex.DriveRef(drive, subnet), key, counter, result, seen, detail);
            }
            for (var host : GridIndex.findCellHosts(scanned)) {
                scanCellHost(new GridIndex.CellHost(host, subnet), key, counter, result, seen, detail);
            }
        }

        // Positional fallback: drives in the loaded chunks around the network's own blocks. This
        // does not depend on the bridge topology at all and catches subnetworks that the graph
        // walk above cannot reach.
        int nearby = 0;
        for (var ref : GridIndex.nearbyDrives(grid)) {
            nearby++;
            scanDrive(ref, key, counter, result, seen, detail);
        }
        for (var host : GridIndex.nearbyCellHosts(grid)) {
            nearby++;
            scanCellHost(new GridIndex.CellHost(host, true), key, counter, result, seen, detail);
        }

        // Log only when the answer changes, so this is readable instead of 4 lines per second.
        String summary = "networks=" + networks + " nearby=" + nearby + " drives=" + driveTotal
                + " hits=" + result.size() + " " + detail;
        if (!summary.equals(lastScanSummary)) {
            lastScanSummary = summary;
            com.yg.ae2itemlocator.AE2ItemLocator.LOG.info("[ae2-disk-locator] scan {}", summary);
        }

        return result;
    }

    private static void scanDrive(GridIndex.DriveRef ref, appeng.api.stacks.AEKey key,
            appeng.api.stacks.KeyCounter counter,
            java.util.List<com.yg.ae2itemlocator.common.CellRef> out,
            java.util.Set<Long> seenPos,
            StringBuilder detail) {
        if (!(ref.drive() instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity)) {
            return;
        }
        if (!seenPos.add(blockEntity.getBlockPos().asLong())) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved()) {
            return;
        }

        int cellCount;
        try {
            cellCount = Math.max(0, ref.drive().getCellCount());
        } catch (Throwable t) {
            return;
        }

        for (int slot = 0; slot < cellCount; slot++) {
            appeng.api.storage.MEStorage inventory;
            try {
                inventory = ref.drive().getCellInventory(slot);
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
            if (counter.get(key) <= 0) {
                continue;
            }

            String cellType = "";
            try {
                var description = inventory.getDescription();
                if (description != null) {
                    cellType = description.getString();
                }
            } catch (Throwable ignored) {
                // cosmetic only
            }

            var cellRef = com.yg.ae2itemlocator.common.CellRef.of(
                    level.dimension(), blockEntity.getBlockPos(), slot, cellType, ref.subnet());
            out.add(cellRef);
            detail.append('(').append(cellRef.pos().getX()).append(',')
                    .append(cellRef.pos().getY()).append(',')
                    .append(cellRef.pos().getZ()).append(") slot ").append(slot)
                    .append(ref.subnet() ? " subnet" : " root").append(' ');
        }
    }

    /**
     * One addon cell host: a single AE cell exposed through {@code getCellInventory()} instead of
     * {@code IChestOrDrive}, so there is no slot number to report.
     */
    private static void scanCellHost(GridIndex.CellHost host, appeng.api.stacks.AEKey key,
            appeng.api.stacks.KeyCounter counter,
            java.util.List<com.yg.ae2itemlocator.common.CellRef> out,
            java.util.Set<Long> seenPos,
            StringBuilder detail) {
        var blockEntity = host.blockEntity();
        if (!seenPos.add(blockEntity.getBlockPos().asLong())) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved()) {
            return;
        }

        appeng.api.storage.MEStorage inventory = CellHostProbe.storage(blockEntity);
        if (inventory == null) {
            return;
        }

        counter.clear();
        try {
            inventory.getAvailableStacks(counter);
        } catch (Throwable t) {
            return;
        }
        if (counter.get(key) <= 0) {
            return;
        }

        var cellRef = new com.yg.ae2itemlocator.common.CellRef(level.dimension(),
                blockEntity.getBlockPos(), com.yg.ae2itemlocator.common.CellRef.NO_SLOT,
                CellHostProbe.cellLabel(blockEntity), host.subnet(),
                CellHostProbe.controllerPos(blockEntity), CellHostProbe.hostSlot(blockEntity));
        out.add(cellRef);
        detail.append('(').append(cellRef.pos().getX()).append(',')
                .append(cellRef.pos().getY()).append(',')
                .append(cellRef.pos().getZ()).append(") cell")
                .append(host.subnet() ? " subnet" : " root").append(' ');
    }

    private static void evictIfNeeded() {
        while (INDEXES.size() > MAX_INDEXES) {
            var it = INDEXES.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    private IndexManager() {
    }
}
