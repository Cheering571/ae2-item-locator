package com.yg.ae2itemlocator.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One "this item sits in that drive slot" record.
 *
 * @param dimension dimension of the drive
 * @param pos       block position of the drive
 * @param slot      slot index inside the drive
 * @param cellType  human readable cell type, e.g. "64k ME Storage Cell"
 * @param subnet    true when the drive lives on a subnetwork reached through a storage bus
 */
public record CellRef(ResourceKey<Level> dimension, BlockPos pos, int slot, String cellType, boolean subnet) {

    public static void write(FriendlyByteBuf buf, CellRef ref) {
        buf.writeResourceLocation(ref.dimension.location());
        buf.writeLong(ref.pos.asLong());
        buf.writeVarInt(ref.slot);
        buf.writeUtf(ref.cellType, 128);
        buf.writeBoolean(ref.subnet);
    }

    public static CellRef read(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        BlockPos pos = BlockPos.of(buf.readLong());
        int slot = buf.readVarInt();
        String cellType = buf.readUtf(128);
        boolean subnet = buf.readBoolean();
        return new CellRef(dimension, pos, slot, cellType, subnet);
    }

    public ResourceLocation dimensionId() {
        return dimension.location();
    }
}
