package com.yg.ae2itemlocator.server;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
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
 * <p>The probe is reflective on purpose: linking against the addon would put it on this mod's
 * compile classpath and make the repository unbuildable without that jar. The shape being matched
 * is the small {@code getCellStack()} / {@code getCellInventory()} contract that addon cell hosts
 * share.
 */
public final class CellHostProbe {

    /** Cached method pairs per class: {@code [inventoryMethod, stackMethod]}, either may be null. */
    private static final Map<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>();

    private static final Method[] NONE = new Method[2];

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

    private static Method[] lookup(Class<?> type) {
        Method[] cached = METHODS.get(type);
        if (cached != null) {
            return cached;
        }

        Method inventory = null;
        Method stack = null;
        try {
            inventory = type.getMethod("getCellInventory");
            if (!MEStorage.class.isAssignableFrom(inventory.getReturnType())) {
                inventory = null;
            }
        } catch (Throwable ignored) {
            inventory = null;
        }
        if (inventory != null) {
            try {
                stack = type.getMethod("getCellStack");
                if (stack.getReturnType() != ItemStack.class) {
                    stack = null;
                }
            } catch (Throwable ignored) {
                stack = null;
            }
        }

        Method[] result = inventory == null ? NONE : new Method[] { inventory, stack };
        METHODS.put(type, result);
        return result;
    }

    private CellHostProbe() {
    }
}
