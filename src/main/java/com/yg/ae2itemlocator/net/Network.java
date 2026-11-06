package com.yg.ae2itemlocator.net;

import com.yg.ae2itemlocator.AE2ItemLocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel.
 *
 * <p>Forge 1.20.1 uses {@link SimpleChannel} instead of NeoForge's payload registrar, so each
 * message carries its own static encode/decode pair. Both version predicates accept anything:
 * a client without this mod never registers the channel, so its messages are simply not
 * delivered rather than kicking the player. One-sided installs stay silent, as before.
 */
public final class Network {
    public static final String VERSION = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AE2ItemLocator.MODID, "main"))
            .clientAcceptedVersions(version -> true)
            .serverAcceptedVersions(version -> true)
            .networkProtocolVersion(() -> VERSION)
            .simpleChannel();

    private static int nextId;

    public static void register() {
        CHANNEL.messageBuilder(DiskQueryPayload.class, nextId++)
                .encoder((msg, buf) -> DiskQueryPayload.encode(buf, msg))
                .decoder(DiskQueryPayload::decode)
                .consumerMainThread(DiskQueryPayload::handle)
                .add();

        CHANNEL.messageBuilder(DiskResultPayload.class, nextId++)
                .encoder((msg, buf) -> DiskResultPayload.encode(buf, msg))
                .decoder(DiskResultPayload::decode)
                .consumerMainThread(DiskResultPayload::handle)
                .add();

        CHANNEL.messageBuilder(HelloPayload.class, nextId++)
                .encoder((msg, buf) -> HelloPayload.encode(buf, msg))
                .decoder(HelloPayload::decode)
                .consumerMainThread(HelloPayload::handle)
                .add();

        CHANNEL.messageBuilder(DriveSlotQueryPayload.class, nextId++)
                .encoder((msg, buf) -> DriveSlotQueryPayload.encode(buf, msg))
                .decoder(DriveSlotQueryPayload::decode)
                .consumerMainThread(DriveSlotQueryPayload::handle)
                .add();

        CHANNEL.messageBuilder(DriveSlotResultPayload.class, nextId++)
                .encoder((msg, buf) -> DriveSlotResultPayload.encode(buf, msg))
                .decoder(DriveSlotResultPayload::decode)
                .consumerMainThread(DriveSlotResultPayload::handle)
                .add();
    }

    public static void toServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    private Network() {
    }
}
