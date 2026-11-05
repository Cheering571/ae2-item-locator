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
 * Client to server: "I have this drive's GUI open, which of its slots hold this item?".
 * Sent while a drive screen is open so the matching slot can be outlined.
 */
public record DriveSlotQueryPayload(int containerId, AEKey key) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DriveSlotQueryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AE2ItemLocator.MODID, "drive_slots"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DriveSlotQueryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DriveSlotQueryPayload::containerId,
                    AEKey.STREAM_CODEC, DriveSlotQueryPayload::key,
                    DriveSlotQueryPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveSlotQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                QueryService.handleDriveSlots(player, payload.containerId(), payload.key());
            }
        });
    }
}
