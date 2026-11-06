package com.yg.ae2itemlocator.mixin;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import com.yg.ae2itemlocator.client.DiskTooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Hooks the single place where AE2 builds the tooltip for a hovered network entry.
 *
 * <p>{@code MEStorageScreen#renderGridInventoryEntryTooltip} starts with
 * {@code AEKeyRendering.getTooltip(entry.getWhat())}, and the returned list is mutable, so we can
 * append our "which drive holds this" lines before AE2 adds its own "stored amount" line.
 *
 * <p>The handler is an instance method on purpose: the target is an instance method too, and
 * Mixin 0.8.5 (Forge 1.20.1) rejects a static handler outright instead of merely skipping it.
 *
 * <p>{@code require = 0} keeps the mod alive if a future AE2 version renames the method; the
 * feature then silently turns off instead of crashing the game.
 */
@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageScreenMixin {

    @Redirect(
            method = "renderGridInventoryEntryTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/client/AEKeyRendering;getTooltip(Lappeng/api/stacks/AEKey;)Ljava/util/List;"),
            require = 0,
            remap = false)
    private List<Component> ae2itemlocator$appendDiskSources(AEKey key) {
        List<Component> lines = AEKeyRendering.getTooltip(key);
        DiskTooltip.appendSources(key, lines);
        return lines;
    }
}
