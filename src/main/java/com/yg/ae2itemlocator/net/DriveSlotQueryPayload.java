package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.server.QueryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: "I have this drive's GUI open, which of its slots hold this item?".
 * Sent while a drive screen is open so the matching slot can be outlined.
 */
public record DriveSlotQueryPayload(int containerId, AEKey key) {

    public static void encode(FriendlyByteBuf buf, DriveSlotQueryPayload payload) {
        buf.writeVarInt(payload.containerId);
        AEKey.writeKey(buf, payload.key);
    }

    public static DriveSlotQueryPayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        AEKey key = AEKey.readKey(buf);
        return new DriveSlotQueryPayload(containerId, key);
    }

    public static void handle(DriveSlotQueryPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() ->
                    QueryService.handleDriveSlots(player, payload.containerId(), payload.key()));
        }
        context.setPacketHandled(true);
    }
}
