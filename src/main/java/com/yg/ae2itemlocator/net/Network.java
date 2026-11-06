package com.yg.ae2itemlocator.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    public static final String VERSION = "4";

    public static void register(RegisterPayloadHandlersEvent event) {
        // "optional" keeps one-sided installs from disconnecting: a client without the mod just
        // ignores the hello packet, and a server without the mod never receives a query.
        PayloadRegistrar registrar = event.registrar(VERSION).optional();

        registrar.playToServer(DiskQueryPayload.TYPE, DiskQueryPayload.STREAM_CODEC, DiskQueryPayload::handle);
        registrar.playToClient(DiskResultPayload.TYPE, DiskResultPayload.STREAM_CODEC, DiskResultPayload::handle);
        registrar.playToClient(HelloPayload.TYPE, HelloPayload.STREAM_CODEC, HelloPayload::handle);
        registrar.playToServer(DriveSlotQueryPayload.TYPE, DriveSlotQueryPayload.STREAM_CODEC,
                DriveSlotQueryPayload::handle);
        registrar.playToClient(DriveSlotResultPayload.TYPE, DriveSlotResultPayload.STREAM_CODEC,
                DriveSlotResultPayload::handle);
    }

    private Network() {
    }
}
