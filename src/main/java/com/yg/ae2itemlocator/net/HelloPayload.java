package com.yg.ae2itemlocator.net;

import com.yg.ae2itemlocator.AE2ItemLocator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent once when a player joins, so the client knows the server side is present.
 * Without it the client stays quiet and never sends a query.
 */
public record HelloPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HelloPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AE2ItemLocator.MODID, "hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> STREAM_CODEC =
            StreamCodec.unit(new HelloPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelloPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBridge.onHello.run());
    }
}
