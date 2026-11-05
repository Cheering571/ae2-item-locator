package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.AE2ItemLocator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the slot indices inside the currently open drive that hold the queried key.
 */
public record DriveSlotResultPayload(AEKey key, int[] slots) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DriveSlotResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AE2ItemLocator.MODID, "drive_slot_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DriveSlotResultPayload> STREAM_CODEC =
            StreamCodec.of(DriveSlotResultPayload::encode, DriveSlotResultPayload::decode);

    private static final int MAX_SLOTS = 64;

    private static void encode(RegistryFriendlyByteBuf buf, DriveSlotResultPayload payload) {
        AEKey.STREAM_CODEC.encode(buf, payload.key);
        int count = Math.min(payload.slots.length, MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(payload.slots[i]);
        }
    }

    private static DriveSlotResultPayload decode(RegistryFriendlyByteBuf buf) {
        AEKey key = AEKey.STREAM_CODEC.decode(buf);
        int count = Math.min(buf.readVarInt(), MAX_SLOTS);
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readVarInt();
        }
        return new DriveSlotResultPayload(key, slots);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveSlotResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBridge.onDriveSlots.accept(payload));
    }
}
