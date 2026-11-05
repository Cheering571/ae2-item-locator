package com.yg.ae2itemlocator.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_SOURCES;
    public static final ModConfigSpec.BooleanValue BEAM;
    public static final ModConfigSpec.IntValue HIGHLIGHT_SECONDS;

    public static final ModConfigSpec.BooleanValue UNIFIED_COLOR;
    public static final ModConfigSpec.IntValue COLOR_UNIFIED;
    public static final ModConfigSpec.IntValue COLOR_BEAM;
    public static final ModConfigSpec.IntValue COLOR_DRIVE;
    public static final ModConfigSpec.IntValue COLOR_SLOT;

    public static final ModConfigSpec.IntValue HUD_X;
    public static final ModConfigSpec.IntValue HUD_Y;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("AE2 Item Locator (client)").push("client");

        MAX_SOURCES = builder
                .comment("Maximum number of drive entries appended to an item tooltip.")
                .defineInRange("maxSources", 3, 1, 12);

        BEAM = builder
                .comment("Draw a vertical light column above every highlighted drive.")
                .define("beam", true);

        HIGHLIGHT_SECONDS = builder
                .comment("Automatically clear a locked highlight after this many seconds.",
                        "0 keeps it until you press the key again.")
                .defineInRange("highlightSeconds", 0, 0, 3600);

        builder.comment("Colors are 0xRRGGBB.").push("colors");
        UNIFIED_COLOR = builder
                .comment("Use a single color for the beam, the drive highlight and the slot highlight.")
                .define("unified", true);
        COLOR_UNIFIED = builder.defineInRange("unifiedValue", 0x66D9FF, 0, 0xFFFFFF);
        COLOR_BEAM = builder.defineInRange("beam", 0x66D9FF, 0, 0xFFFFFF);
        COLOR_DRIVE = builder.defineInRange("drive", 0x66D9FF, 0, 0xFFFFFF);
        COLOR_SLOT = builder.defineInRange("slot", 0x66D9FF, 0, 0xFFFFFF);
        builder.pop();

        builder.comment("Position of the on-screen status text, in permille of the screen size.").push("hud");
        HUD_X = builder.defineInRange("x", 8, 0, 1000);
        HUD_Y = builder.defineInRange("y", 8, 0, 1000);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean unified() {
        return UNIFIED_COLOR.get();
    }

    public static int beamColor() {
        return argb(COLOR_UNIFIED.get(), COLOR_BEAM.get());
    }

    public static int driveColor() {
        return argb(COLOR_UNIFIED.get(), COLOR_DRIVE.get());
    }

    public static int slotColor() {
        return argb(COLOR_UNIFIED.get(), COLOR_SLOT.get());
    }

    private static int argb(int unified, int separate) {
        int rgb = unified() ? unified : separate;
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private ClientConfig() {
    }
}
