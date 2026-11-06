package com.yg.ae2itemlocator.server;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.parts.AEBasePart;
import appeng.parts.storagebus.StorageBusPart;
import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A lazy, tick-sliced catalog of "which drive slot holds which AE key" for one ME network.
 *
 * <p>AE2's {@code IStorageService#getInventory()} is an aggregated view and deliberately forgets
 * where a stack came from, so the only correct source of physical location is the drive host
 * itself: {@link IChestOrDrive} exposes the per-slot {@link MEStorage} that is mounted on the grid.
 */
public final class GridIndex {

    /** Logs the very first completed build once, so a user can verify the index is non-empty. */
    private static boolean loggedFirstBuild;

    public enum State {
        BUILDING,
        READY
    }

    private final Map<AEKey, List<CellRef>> byKey = new HashMap<>();
    private final IGrid grid;
    private final List<DriveRef> drives;
    /** Addon cell hosts (drives that store their contents in a cell item but are not IChestOrDrive). */
    private final List<CellHost> cellHosts;
    private final int totalCells;

    /** A drive plus whether it was reached through a storage bus into a subnetwork. */
    public record DriveRef(IChestOrDrive drive, boolean subnet) {
    }

    /**
     * A block entity that owns a single AE cell but does not implement {@code IChestOrDrive}, such
     * as Neo ECO AE's storage matrix drive. See {@link CellHostProbe}.
     */
    public record CellHost(BlockEntity blockEntity, boolean subnet) {
    }

    /**
     * Data from the previous build of the same grid. While a rebuild is in progress we keep
     * answering from this snapshot instead of making the tooltip fall back to "indexing…".
     */
    private final Map<AEKey, List<CellRef>> fallback;

    private int driveCursor;
    private int slotCursor;
    private int cellHostCursor;
    private int scannedCells;
    private State state = State.BUILDING;

    /** When the last successful build started; drives TTL. */
    private final long builtAt = System.currentTimeMillis();
    /** Used only for LRU eviction of idle grids. */
    private long lastAccess = System.currentTimeMillis();

    /** Number of distinct keys the network reported when this index was finished. */
    private int builtKeyCount = -1;
    /** Number of drive hosts when the index was built. */
    private int builtDriveCount = -1;
    /** Set when the network's key set no longer matches what we indexed. */
    private boolean dirty;

    public GridIndex(IGrid grid) {
        this(grid, null);
    }

    public GridIndex(IGrid grid, GridIndex previous) {
        this.grid = grid;
        this.fallback = previous != null && previous.state == State.READY ? previous.byKey : null;

        // The terminal's own network plus any subnetwork reachable through a storage bus.
        var allDrives = new ArrayList<DriveRef>();
        var seenPos = new java.util.HashSet<Long>();

        for (IGrid scanned : collectGrids(grid)) {
            boolean subnet = scanned != grid;
            for (IChestOrDrive drive : findDrives(scanned)) {
                if (drive instanceof BlockEntity be && !seenPos.add(be.getBlockPos().asLong())) {
                    continue;
                }
                allDrives.add(new DriveRef(drive, subnet));
            }
        }

        // Positional fallback so the index and the live scan agree about subnet drives.
        for (DriveRef ref : nearbyDrives(grid)) {
            if (ref.drive() instanceof BlockEntity be && !seenPos.add(be.getBlockPos().asLong())) {
                continue;
            }
            allDrives.add(ref);
        }

        this.drives = allDrives;

        // Addon cell hosts: same networks, same positional fallback, different storage API.
        var hosts = new ArrayList<CellHost>();
        for (IGrid scanned : collectGrids(grid)) {
            boolean subnet = scanned != grid;
            for (BlockEntity be : findCellHosts(scanned)) {
                if (!seenPos.add(be.getBlockPos().asLong())) {
                    continue;
                }
                hosts.add(new CellHost(be, subnet));
            }
        }
        for (BlockEntity be : nearbyCellHosts(grid)) {
            if (!seenPos.add(be.getBlockPos().asLong())) {
                continue;
            }
            hosts.add(new CellHost(be, true));
        }
        this.cellHosts = hosts;

        int total = 0;
        for (DriveRef ref : drives) {
            try {
                total += Math.max(0, ref.drive().getCellCount());
            } catch (Throwable ignored) {
                // A misbehaving addon drive must not break the whole index.
            }
        }
        this.totalCells = total + cellHosts.size();

        // Snapshot the network's key count at build start. If it moved while we were scanning,
        // pollDirty() will notice the mismatch afterwards and trigger another pass.
        this.builtKeyCount = currentKeyCount();
        // Drive count is polled on the root network only, to stay comparable with currentDriveCount().
        this.builtDriveCount = findDrives(grid).size();

        if (this.drives.isEmpty()) {
            this.state = State.READY;
        }
    }

    public State state() {
        return state;
    }

    /**
     * Collect every drive-like machine on the grid.
     *
     * <p>Do <b>not</b> use {@code grid.getMachines(IChestOrDrive.class)}: AE2's implementation is
     * {@code machines.get(machineClass)} on a {@code SetMultimap} keyed by the concrete machine
     * class, so an interface lookup returns an empty set (this is exactly the bug that made every
     * lookup report "not stored in any drive"). Walking the nodes and filtering with
     * {@code instanceof} is index-independent and always correct.
     */
    static List<IChestOrDrive> findDrives(IGrid grid) {
        var found = new LinkedHashSet<IChestOrDrive>();
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof IChestOrDrive drive) {
                found.add(drive);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * Addon drives that keep their storage in a cell item but are not {@code IChestOrDrive}
     * (Neo ECO AE's storage matrix drives are the reference case).
     */
    static List<BlockEntity> findCellHosts(IGrid grid) {
        var found = new ArrayList<BlockEntity>();
        var seen = new java.util.HashSet<Long>();
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof BlockEntity be
                    && !(be instanceof IChestOrDrive)
                    && CellHostProbe.supports(be)
                    && seen.add(be.getBlockPos().asLong())) {
                found.add(be);
            }
        }
        return found;
    }

    /**
     * @return 0..100
     */
    public int progress() {
        if (state == State.READY) {
            return 100;
        }
        if (totalCells <= 0) {
            return 0;
        }
        return Math.min(99, (int) ((long) scannedCells * 100L / totalCells));
    }

    public List<CellRef> get(AEKey key) {
        if (state == State.READY) {
            return byKey.getOrDefault(key, Collections.emptyList());
        }
        if (fallback != null) {
            return fallback.getOrDefault(key, Collections.emptyList());
        }
        return Collections.emptyList();
    }

    /**
     * True when a lookup can be answered right now, even if a rebuild is still running.
     */
    public boolean hasAnswer() {
        return state == State.READY || fallback != null;
    }

    public int driveCount() {
        return drives.size();
    }

    public void touch() {
        lastAccess = System.currentTimeMillis();
    }

    public long lastAccess() {
        return lastAccess;
    }

    public long builtAt() {
        return builtAt;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Cheap staleness probe, meant to be called once per server tick: compares the number of
     * distinct keys the network currently reports with the number we indexed.
     *
     * <p>This is what makes a freshly inserted cell (or a drive in another dimension) show up
     * within a second instead of waiting for the TTL. It deliberately looks at the <em>key count</em>
     * rather than the items themselves: {@code getCachedInventory()} is maintained by AE2 at most
     * once per tick, and {@code size()} is O(distinct primary keys), so polling it is cheap.
     */
    public void pollDirty() {
        if (state != State.READY || dirty) {
            return;
        }

        // Structural change: a drive was added or removed (covers "new drive in another dimension
        // connected through a quantum bridge", even when the inserted cell holds known items).
        if (builtDriveCount >= 0 && currentDriveCount() != builtDriveCount) {
            dirty = true;
            return;
        }

        // Content change: new item types appeared or disappeared.
        int current = currentKeyCount();
        if (current >= 0 && builtKeyCount >= 0 && current != builtKeyCount) {
            dirty = true;
        }
    }

    private int currentDriveCount() {
        int count = 0;
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof IChestOrDrive) {
                count++;
            }
        }
        return count;
    }

    private int currentKeyCount() {
        try {
            return grid.getStorageService().getCachedInventory().size();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Advance the scan, respecting both a cell count and a wall-clock budget.
     */
    public void step(int maxCells, long deadlineNanos) {
        if (state == State.READY) {
            return;
        }

        int processed = 0;
        while (processed < maxCells && System.nanoTime() < deadlineNanos) {
            if (driveCursor >= drives.size()) {
                break;
            }

            DriveRef ref = drives.get(driveCursor);
            IChestOrDrive drive = ref.drive();
            int slotCount;
            try {
                slotCount = Math.max(0, drive.getCellCount());
            } catch (Throwable t) {
                slotCount = 0;
            }

            if (slotCursor >= slotCount) {
                driveCursor++;
                slotCursor = 0;
                continue;
            }

            scanSlot(ref, slotCursor);
            slotCursor++;
            scannedCells++;
            processed++;
        }

        // Cell hosts come after the regular drives, one block at a time.
        while (processed < maxCells && System.nanoTime() < deadlineNanos
                && cellHostCursor < cellHosts.size()) {
            scanCellHost(cellHosts.get(cellHostCursor));
            cellHostCursor++;
            scannedCells++;
            processed++;
        }

        if (driveCursor >= drives.size() && cellHostCursor >= cellHosts.size()) {
            finish();
        }
    }

    /**
     * Reads one addon cell host: a single cell item, so there is no slot number to report.
     */
    private void scanCellHost(CellHost host) {
        BlockEntity blockEntity = host.blockEntity();
        Level level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved()) {
            return;
        }

        MEStorage inventory = CellHostProbe.storage(blockEntity);
        if (inventory == null) {
            return;
        }

        KeyCounter counter = new KeyCounter();
        try {
            inventory.getAvailableStacks(counter);
        } catch (Throwable t) {
            return;
        }
        if (counter.isEmpty()) {
            return;
        }

        CellRef cellRef = new CellRef(level.dimension(), blockEntity.getBlockPos(), CellRef.NO_SLOT,
                CellHostProbe.cellLabel(blockEntity), host.subnet(),
                CellHostProbe.controllerPos(blockEntity), CellHostProbe.hostSlot(blockEntity));
        for (AEKey key : counter.keySet()) {
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>(2)).add(cellRef);
        }
    }

    private void scanSlot(DriveRef ref, int slot) {
        IChestOrDrive drive = ref.drive();
        if (!(drive instanceof BlockEntity blockEntity)) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (level == null || blockEntity.isRemoved()) {
            return;
        }

        MEStorage inventory;
        try {
            inventory = drive.getCellInventory(slot);
        } catch (Throwable t) {
            return;
        }
        if (inventory == null) {
            return;
        }

        KeyCounter counter = new KeyCounter();
        try {
            inventory.getAvailableStacks(counter);
        } catch (Throwable t) {
            AE2ItemLocator.LOG.debug("Failed to read drive contents at {} slot {}", blockEntity.getBlockPos(), slot, t);
            return;
        }
        if (counter.isEmpty()) {
            return;
        }

        String cellType = "";
        try {
            Component description = inventory.getDescription();
            if (description != null) {
                cellType = description.getString();
            }
        } catch (Throwable ignored) {
            // cosmetic only
        }

        ResourceKey<Level> dimension = level.dimension();
        BlockPos pos = blockEntity.getBlockPos();
        CellRef cellRef = CellRef.of(dimension, pos, slot, cellType, ref.subnet());

        for (AEKey key : counter.keySet()) {
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>(2)).add(cellRef);
        }
    }

    /**
     * The network itself plus every subnetwork reachable by walking the storage buses on it.
     *
     * <p>A storage bus facing an ME interface of another network exposes that network's storage to
     * this one, which is why a main-network terminal can show subnet items. Those items live on
     * drives of the other grid, so scanning only the terminal's own grid would miss them entirely.
     */
    static List<IGrid> collectGrids(IGrid root) {
        var result = new ArrayList<IGrid>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        var queue = new ArrayDeque<IGrid>();
        queue.add(root);
        visited.add(root);

        for (int depth = 0; depth < 3 && !queue.isEmpty(); depth++) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                IGrid current = queue.poll();
                result.add(current);
                for (IGridNode node : current.getNodes()) {
                    for (IGrid linked : linkedGrids(node)) {
                        if (visited.add(linked)) {
                            queue.add(linked);
                        }
                    }
                    // Fallback that does not depend on "which block faces which": AE networks only
                    // touch their own cables, so a neighbouring grid that is not this one is a
                    // bridge (interface / quantum link / storage bus). Capped so a pathological
                    // base cannot make this scan unbounded.
                    if (visited.size() < 8) {
                        for (IGrid linked : neighbourGrids(node)) {
                            if (visited.add(linked)) {
                                queue.add(linked);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Networks reachable through this node when the node is (or hosts) a storage bus.
     *
     * <p>Handles both ownership models AE2 uses for parts: the node owner may be the part itself
     * ({@code AEBasePart}) or the cable bus block entity that hosts it.
     */
    private static List<IGrid> linkedGrids(IGridNode node) {
        var found = new ArrayList<IGrid>(1);

        Object owner = node.getOwner();
        if (owner instanceof StorageBusPart part) {
            addLinked(part.getHost(), part.getSide(), found);
        } else if (owner instanceof appeng.api.parts.IPartHost host) {
            for (var side : net.minecraft.core.Direction.values()) {
                if (host.getPart(side) instanceof StorageBusPart) {
                    addLinked(host, side, found);
                }
            }
        }
        return found;
    }

    /**
     * Grids of the block entities directly adjacent to this node's host.
     *
     * <p>In AE2 two different grids can only touch each other through a bridge (interface, quantum
     * link, storage bus on a foreign block), so any adjacent grid that is not the current one is
     * worth scanning. This does not require knowing which side a part faces.
     */
    private static List<IGrid> neighbourGrids(IGridNode node) {
        Object owner = node.getOwner();
        BlockEntity ownerBe = null;
        if (owner instanceof BlockEntity be) {
            ownerBe = be;
        } else if (owner instanceof AEBasePart part && part.getHost() instanceof BlockEntity hostBe) {
            ownerBe = hostBe;
        } else if (owner instanceof appeng.api.parts.IPartHost host
                && host instanceof BlockEntity hostBe2) {
            ownerBe = hostBe2;
        }
        if (ownerBe == null) {
            return List.of();
        }

        var level = ownerBe.getLevel();
        if (level == null) {
            return List.of();
        }

        var out = new ArrayList<IGrid>(1);
        var pos = ownerBe.getBlockPos();
        for (var dir : net.minecraft.core.Direction.values()) {
            var adjacent = level.getBlockEntity(pos.relative(dir));
            if (adjacent == null || adjacent == ownerBe) {
                continue;
            }
            IGridNode other = null;
            if (adjacent instanceof appeng.api.networking.security.IActionHost actionHost) {
                other = actionHost.getActionableNode();
            }
            if (other == null && adjacent instanceof IInWorldGridNodeHost nodeHost) {
                other = nodeHost.getGridNode(dir.getOpposite());
            }
            if (other != null && other.getGrid() != null) {
                out.add(other.getGrid());
            }
        }
        return out;
    }

    /**
     * Drives in loaded chunks around the network's own blocks.
     *
     * <p>Topology-based discovery (storage bus / neighbour walk) can stop short when several small
     * networks sit around the base, so this is a bounded, purely positional fallback: any drive that
     * physically sits next to the terminal's network is almost certainly part of it, possibly as a
     * subnetwork. Callers still verify that a drive actually contains the queried key.
     */
    static List<DriveRef> nearbyDrives(IGrid root) {
        var result = new ArrayList<DriveRef>();
        forEachNearbyBlockEntity(root, blockEntity -> {
            if (blockEntity instanceof IChestOrDrive drive) {
                result.add(new DriveRef(drive, true));
            }
        });
        return result;
    }

    /**
     * Same positional fallback, for addon cell hosts instead of {@code IChestOrDrive} drives.
     */
    static List<BlockEntity> nearbyCellHosts(IGrid root) {
        var result = new ArrayList<BlockEntity>();
        forEachNearbyBlockEntity(root, blockEntity -> {
            if (!(blockEntity instanceof IChestOrDrive) && CellHostProbe.supports(blockEntity)) {
                result.add(blockEntity);
            }
        });
        return result;
    }

    private static void forEachNearbyBlockEntity(IGrid root,
            java.util.function.Consumer<BlockEntity> consumer) {
        var byLevel = new IdentityHashMap<Level, List<BlockPos>>();

        for (IGridNode node : root.getNodes()) {
            Object owner = node.getOwner();
            BlockEntity be = null;
            if (owner instanceof BlockEntity b) {
                be = b;
            } else if (owner instanceof AEBasePart part && part.getHost() instanceof BlockEntity host) {
                be = host;
            } else if (owner instanceof appeng.api.parts.IPartHost host
                    && host instanceof BlockEntity hostBe) {
                be = hostBe;
            }
            if (be != null && be.getLevel() != null) {
                byLevel.computeIfAbsent(be.getLevel(), k -> new ArrayList<>()).add(be.getBlockPos());
            }
        }

        var seenPos = new java.util.HashSet<Long>();
        for (var entry : byLevel.entrySet()) {
            Level level = entry.getKey();
            var positions = entry.getValue();
            if (positions.isEmpty()) {
                continue;
            }

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            // Two chunks of margin, and a hard cap so a spread-out network cannot blow this up.
            int minChunkX = (minX >> 4) - 2;
            int maxChunkX = (maxX >> 4) + 2;
            int minChunkZ = (minZ >> 4) - 2;
            int maxChunkZ = (maxZ >> 4) + 2;
            if ((long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1) > 256L) {
                continue;
            }

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (!seenPos.add(blockEntity.getBlockPos().asLong())) {
                            continue;
                        }
                        consumer.accept(blockEntity);
                    }
                }
            }
        }
    }

    private static void addLinked(appeng.api.parts.IPartHost host, net.minecraft.core.Direction side,
            List<IGrid> out) {
        if (!(host instanceof BlockEntity hostBe)) {
            return;
        }
        var level = hostBe.getLevel();
        if (level == null) {
            return;
        }

        var targetBe = level.getBlockEntity(hostBe.getBlockPos().relative(side));
        if (targetBe == null) {
            return;
        }

        // Prefer the actionable node: it ignores per-side connection settings, which
        // getGridNode(side) respects. The block on the other side of a storage bus is the
        // bridge into the other network.
        IGridNode other = null;
        if (targetBe instanceof appeng.api.networking.security.IActionHost actionHost) {
            other = actionHost.getActionableNode();
        }
        if (other == null && targetBe instanceof IInWorldGridNodeHost nodeHost) {
            other = nodeHost.getGridNode(side.getOpposite());
        }
        if (other != null && other.getGrid() != null) {
            out.add(other.getGrid());
        }
    }

    private void finish() {
        state = State.READY;
        dirty = false;
        touch();

        if (!loggedFirstBuild) {
            loggedFirstBuild = true;
            AE2ItemLocator.LOG.info("[ae2-disk-locator] index built: {} drive(s), {} cell slot(s), {} key(s)",
                    drives.size(), scannedCells, byKey.size());
        }
    }
}
