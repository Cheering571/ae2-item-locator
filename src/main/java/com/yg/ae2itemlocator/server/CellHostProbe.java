package com.yg.ae2itemlocator.server;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duck-typed probe for addon drives that keep their storage in a cell item but do not implement
 * AE2's {@code IChestOrDrive}.
 *
 * <p>Neo ECO AE's {@code eco_drive} is the reference case: it implements {@code IStorageProvider},
 * holds one cell item and exposes it through {@code getCellInventory()} returning an
 * {@code IECOStorageCell}, which is an AE2 {@code StorageCell} (therefore an {@code MEStorage}).
 * Because the drive never reports a cell count or a per-slot inventory, a plain
 * {@code IChestOrDrive} walk cannot see it at all.
 *
 * <p>The same block is part of a multiblock, and the controller exposes the ordered list of its
 * drives; both pieces are probed here so the world highlight can draw a link line and the
 * controller GUI can work out which page a drive sits on.
 *
 * <p>The probe is reflective on purpose: linking against the addon would put it on this mod's
 * compile classpath and make the repository unbuildable without that jar.
 */
public final class CellHostProbe {

    /** Cached per class: {@code [inventoryMethod, stackMethod, controllerMethod]}, entries may be null. */
    private static final Map<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>();

    private static final Method[] NONE = new Method[3];

    /**
     * @return the cell storage of this block entity, or {@code null} when it holds no cell or does
     *         not follow the cell-host contract.
     */
    public static MEStorage storage(BlockEntity blockEntity) {
        Method inventory = lookup(blockEntity.getClass())[0];
        if (inventory == null) {
            return null;
        }
        try {
            return inventory.invoke(blockEntity) instanceof MEStorage storage ? storage : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @return the display name of the installed cell, or an empty string.
     */
    public static String cellLabel(BlockEntity blockEntity) {
        Method stack = lookup(blockEntity.getClass())[1];
        if (stack == null) {
            return "";
        }
        try {
            if (stack.invoke(blockEntity) instanceof ItemStack item && !item.isEmpty()) {
                return item.getHoverName().getString();
            }
        } catch (Throwable ignored) {
            // cosmetic only
        }
        return "";
    }

    /**
     * Cheap pre-filter: only AE storage providers can own an AE cell, and this keeps reflection
     * away from the thousands of unrelated block entities a base contains.
     */
    public static boolean supports(BlockEntity blockEntity) {
        return blockEntity instanceof IStorageProvider && lookup(blockEntity.getClass())[0] != null;
    }

    /**
     * @return position of the multiblock controller this cell host belongs to, or {@code null} when
     *         it is not part of one.
     */
    /**
     * Resolves the multiblock controller of a cell host: first through the documented
     * {@code getStorageController()}, then — if that returns nothing — by looking for a nearby
     * controller whose drive list contains this very block entity. The fallback exists because the
     * direct accessor depends on the addon having rebuilt its cluster reference, which does not
     * always happen at the moment we scan.
     *
     * @return controller position plus this drive's index in its list, or {@code null} when the
     *         drive is not part of a controller at all.
     */
    public static ControllerRef controller(BlockEntity blockEntity) {
        BlockPos direct = controllerPos(blockEntity);
        if (direct != null) {
            return new ControllerRef(direct, hostSlot(blockEntity));
        }
        return scanNearbyController(blockEntity);
    }

    public record ControllerRef(BlockPos pos, int slot) {
    }

    private static ControllerRef scanNearbyController(BlockEntity drive) {
        var level = drive.getLevel();
        if (level == null) {
            return null;
        }

        BlockPos origin = drive.getBlockPos();
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity candidate : chunk.getBlockEntities().values()) {
                    if (candidate == drive) {
                        continue;
                    }
                    Method drives = drivesMethod(candidate.getClass());
                    if (drives == null) {
                        continue;
                    }
                    try {
                        if (drives.invoke(candidate) instanceof java.util.List<?> list) {
                            for (int i = 0; i < list.size(); i++) {
                                if (list.get(i) == drive) {
                                    return new ControllerRef(candidate.getBlockPos(), i);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                        // keep looking
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos controllerPos(BlockEntity blockEntity) {
        Method controller = lookup(blockEntity.getClass())[2];
        if (controller == null) {
            return null;
        }
        try {
            if (controller.invoke(blockEntity) instanceof BlockEntity control
                    && control.getLevel() != null && !control.isRemoved()) {
                return control.getBlockPos();
            }
        } catch (Throwable ignored) {
            // optional integration only
        }
        return null;
    }

    /**
     * @return index of this drive inside its controller's cell list, or {@code -1} when unknown.
     *         The controller GUI lists drives in this order, so the client can turn the index into
     *         a page number.
     */
    public static int hostSlot(BlockEntity blockEntity) {
        Method controllerMethod = lookup(blockEntity.getClass())[2];
        if (controllerMethod == null) {
            return -1;
        }
        try {
            if (!(controllerMethod.invoke(blockEntity) instanceof BlockEntity controller)) {
                return -1;
            }
            Method drives = drivesMethod(controller.getClass());
            if (drives == null) {
                return -1;
            }
            if (drives.invoke(controller) instanceof java.util.List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == blockEntity) {
                        return i;
                    }
                }
            }
        } catch (Throwable ignored) {
            // optional integration only
        }
        return -1;
    }

    private static Method[] lookup(Class<?> type) {
        Method[] cached = METHODS.get(type);
        if (cached != null) {
            return cached;
        }

        Method inventory = null;
        Method stack = null;
        Method controller = null;
        try {
            Method candidate = type.getMethod("getCellInventory");
            if (MEStorage.class.isAssignableFrom(candidate.getReturnType())) {
                inventory = candidate;
            }
        } catch (Throwable ignored) {
            inventory = null;
        }
        if (inventory != null) {
            try {
                Method candidate = type.getMethod("getCellStack");
                if (candidate.getReturnType() == ItemStack.class) {
                    stack = candidate;
                }
            } catch (Throwable ignored) {
                stack = null;
            }
            try {
                Method candidate = type.getMethod("getStorageController");
                if (BlockEntity.class.isAssignableFrom(candidate.getReturnType())) {
                    controller = candidate;
                }
            } catch (Throwable ignored) {
                controller = null;
            }
        }

        Method[] result = inventory == null ? NONE : new Method[] { inventory, stack, controller };
        METHODS.put(type, result);
        return result;
    }

    private static Method drivesMethod(Class<?> type) {
        try {
            Method candidate = type.getMethod("getStorageUiDrives");
            if (java.util.List.class.isAssignableFrom(candidate.getReturnType())) {
                return candidate;
            }
        } catch (Throwable ignored) {
            // optional integration only
        }
        return null;
    }

    private CellHostProbe() {
    }
}
