package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.server.QueryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: "which drive holds this key, for the terminal I currently have open?".
 * The terminal is identified by the open container id, which the server verifies.
 */
public record DiskQueryPayload(int containerId, AEKey key, boolean precise) {

    public static void encode(FriendlyByteBuf buf, DiskQueryPayload payload) {
        buf.writeVarInt(payload.containerId);
        AEKey.writeKey(buf, payload.key);
        buf.writeBoolean(payload.precise);
    }

    public static DiskQueryPayload decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        AEKey key = AEKey.readKey(buf);
        boolean precise = buf.readBoolean();
        return new DiskQueryPayload(containerId, key, precise);
    }

    public static void handle(DiskQueryPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() ->
                    QueryService.handle(player, payload.containerId(), payload.key(), payload.precise()));
        }
        context.setPacketHandled(true);
    }
}
