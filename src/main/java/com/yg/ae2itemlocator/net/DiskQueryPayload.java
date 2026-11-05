package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.server.QueryService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: "which drive holds this key, for the terminal I currently have open?".
 * The terminal is identified by the open container id, which the server verifies.
 */
public record DiskQueryPayload(int containerId, AEKey key, boolean precise) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiskQueryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AE2ItemLocator.MODID, "query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskQueryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DiskQueryPayload::containerId,
                    AEKey.STREAM_CODEC, DiskQueryPayload::key,
                    ByteBufCodecs.BOOL, DiskQueryPayload::precise,
                    DiskQueryPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiskQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QueryService.handle(player, payload.containerId(), payload.key(), payload.precise());
            }
        });
    }
}
