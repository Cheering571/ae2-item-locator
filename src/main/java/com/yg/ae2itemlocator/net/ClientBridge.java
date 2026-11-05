package com.yg.ae2itemlocator.net;

import java.util.function.Consumer;

/**
 * Side-agnostic indirection so payload classes never reference client-only types.
 * The client fills these in during {@code ClientSetup#init}.
 */
public final class ClientBridge {
    public static Runnable onHello = () -> {
    };

    public static Consumer<DiskResultPayload> onResult = payload -> {
    };

    public static Consumer<DriveSlotResultPayload> onDriveSlots = payload -> {
    };

    private ClientBridge() {
    }
}
