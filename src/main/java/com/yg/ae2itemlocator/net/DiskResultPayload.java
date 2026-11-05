package com.yg.ae2itemlocator.net;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client answer for a single key.
 */
public record DiskResultPayload(AEKey key, byte state, int progress, List<CellRef> refs) implements CustomPacketPayload {

    /** Index is still being built; {@link #progress} is 0..100. */
    public static final byte BUILDING = 0;
    /** Index is ready; {@link #refs} holds every drive slot containing the key. */
    public static final byte READY = 1;
    /** The key is known to the terminal, but no drive holds it (e.g. craftable only). */
    public static final byte NONE = 2;

    /** Hard cap so a malicious or broken server cannot blow up the client. */
    public static final int MAX_REFS = 32;

    public static final CustomPacketPayload.Type<DiskResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AE2ItemLocator.MODID, "result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskResultPayload> STREAM_CODEC =
            StreamCodec.of(DiskResultPayload::encode, DiskResultPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, DiskResultPayload payload) {
        AEKey.STREAM_CODEC.encode(buf, payload.key);
        buf.writeByte(payload.state);
        buf.writeVarInt(payload.progress);
        int count = Math.min(payload.refs.size(), MAX_REFS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            CellRef.write(buf, payload.refs.get(i));
        }
    }

    private static DiskResultPayload decode(RegistryFriendlyByteBuf buf) {
        AEKey key = AEKey.STREAM_CODEC.decode(buf);
        byte state = buf.readByte();
        int progress = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), MAX_REFS);
        List<CellRef> refs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            refs.add(CellRef.read(buf));
        }
        return new DiskResultPayload(key, state, progress, refs);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DiskResultPayload payload, IPayloadContext context) {
        // ClientBridge holds no client-only types, so this is safe to reference on a server.
        context.enqueueWork(() -> ClientBridge.onResult.accept(payload));
    }
}
