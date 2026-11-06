package com.yg.ae2itemlocator.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent once when a player joins, so the client knows the server side is present.
 * Without it the client stays quiet and never sends a query.
 */
public record HelloPayload() {

    public static void encode(FriendlyByteBuf buf, HelloPayload payload) {
        // no data
    }

    public static HelloPayload decode(FriendlyByteBuf buf) {
        return new HelloPayload();
    }

    public static void handle(HelloPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientBridge.onHello.run());
        context.setPacketHandled(true);
    }
}
