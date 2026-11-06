package com.yg.ae2itemlocator.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * One "this item sits in that drive slot" record.
 *
 * @param dimension dimension of the drive
 * @param pos       block position of the drive
 * @param slot      slot index inside the drive, or {@link #NO_SLOT} for a single-cell storage block
 * @param cellType  human readable cell type, e.g. "64k ME Storage Cell"
 * @param subnet    true when the drive lives on a subnetwork reached through a storage bus
 * @param link      position of the multiblock controller this drive belongs to, or null
 * @param hostSlot  index of this drive inside its controller's cell list, or {@link #NO_HOST_SLOT}
 */
public record CellRef(ResourceKey<Level> dimension, BlockPos pos, int slot, String cellType, boolean subnet,
        @Nullable BlockPos link, int hostSlot) {

    /** Marks a storage block that has no slot concept (a single-cell storage matrix drive). */
    public static final int NO_SLOT = -1;

    /** Marks "not part of a multiblock controller, or the index is unknown". */
    public static final int NO_HOST_SLOT = -1;

    /** Convenience for plain AE2 drives: no controller link, no host index. */
    public static CellRef of(ResourceKey<Level> dimension, BlockPos pos, int slot, String cellType,
            boolean subnet) {
        return new CellRef(dimension, pos, slot, cellType, subnet, null, NO_HOST_SLOT);
    }

    public static void write(FriendlyByteBuf buf, CellRef ref) {
        buf.writeResourceLocation(ref.dimension.location());
        buf.writeLong(ref.pos.asLong());
        buf.writeVarInt(ref.slot);
        buf.writeUtf(ref.cellType, 128);
        buf.writeBoolean(ref.subnet);

        buf.writeBoolean(ref.link != null);
        if (ref.link != null) {
            buf.writeLong(ref.link.asLong());
        }
        buf.writeVarInt(ref.hostSlot);
    }

    public static CellRef read(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        BlockPos pos = BlockPos.of(buf.readLong());
        int slot = buf.readVarInt();
        String cellType = buf.readUtf(128);
        boolean subnet = buf.readBoolean();
        BlockPos link = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
        int hostSlot = buf.readVarInt();
        return new CellRef(dimension, pos, slot, cellType, subnet, link, hostSlot);
    }

    public ResourceLocation dimensionId() {
        return dimension.location();
    }
}
