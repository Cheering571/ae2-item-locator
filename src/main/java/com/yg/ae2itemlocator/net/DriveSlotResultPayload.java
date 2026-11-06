package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the slot indices inside the currently open drive that hold the queried key.
 */
public record DriveSlotResultPayload(AEKey key, int[] slots) {

    private static final int MAX_SLOTS = 64;

    public static void encode(FriendlyByteBuf buf, DriveSlotResultPayload payload) {
        AEKey.writeKey(buf, payload.key);
        int count = Math.min(payload.slots.length, MAX_SLOTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(payload.slots[i]);
        }
    }

    public static DriveSlotResultPayload decode(FriendlyByteBuf buf) {
        AEKey key = AEKey.readKey(buf);
        int count = Math.min(buf.readVarInt(), MAX_SLOTS);
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readVarInt();
        }
        return new DriveSlotResultPayload(key, slots);
    }

    public static void handle(DriveSlotResultPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientBridge.onDriveSlots.accept(payload));
        context.setPacketHandled(true);
    }
}
