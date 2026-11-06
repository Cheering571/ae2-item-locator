package com.yg.ae2itemlocator.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration.
 *
 * <p>Every entry carries an explicit translation key so the NeoForge mod-list config screen follows
 * the game language (the key plus {@code .tooltip}); the English comment stays as the fallback for
 * languages we do not ship.
 */
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

    public static final ModConfigSpec.BooleanValue RAINBOW_UNIFIED;
    public static final ModConfigSpec.BooleanValue RAINBOW_BEAM;
    public static final ModConfigSpec.BooleanValue RAINBOW_DRIVE;
    public static final ModConfigSpec.BooleanValue RAINBOW_SLOT;

    public static final ModConfigSpec.IntValue HUD_X;
    public static final ModConfigSpec.IntValue HUD_Y;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.translation("ae2itemlocator.config.client")
                .comment("AE2 Item Locator (client)")
                .push("client");

        MAX_SOURCES = builder
                .translation("ae2itemlocator.config.client.maxSources")
                .comment("Maximum number of drive entries appended to an item tooltip.")
                .defineInRange("maxSources", 3, 1, 12);

        BEAM = builder
                .translation("ae2itemlocator.config.client.beam")
                .comment("Draw a vertical light column above every highlighted drive.")
                .define("beam", true);

        HIGHLIGHT_SECONDS = builder
                .translation("ae2itemlocator.config.client.highlightSeconds")
                .comment("Automatically clear a locked highlight after this many seconds.",
                        "0 keeps it until you press the key again.")
                .defineInRange("highlightSeconds", 0, 0, 3600);

        builder.translation("ae2itemlocator.config.client.colors")
                .comment("Colors are 0xRRGGBB.")
                .push("colors");

        UNIFIED_COLOR = builder
                .translation("ae2itemlocator.config.client.colors.unified")
                .comment("Use a single color for the beam, the drive highlight and the slot highlight.")
                .define("unified", true);

        COLOR_UNIFIED = builder
                .translation("ae2itemlocator.config.client.colors.unifiedValue")
                .comment("Color used while 'unified' is enabled.")
                .defineInRange("unifiedValue", 0x66D9FF, 0, 0xFFFFFF);

        COLOR_BEAM = builder
                .translation("ae2itemlocator.config.client.colors.beam")
                .comment("Color of the beacon beam when 'unified' is disabled.")
                .defineInRange("beam", 0x66D9FF, 0, 0xFFFFFF);

        COLOR_DRIVE = builder
                .translation("ae2itemlocator.config.client.colors.drive")
                .comment("Color of the drive highlight when 'unified' is disabled.")
                .defineInRange("drive", 0x66D9FF, 0, 0xFFFFFF);

        COLOR_SLOT = builder
                .translation("ae2itemlocator.config.client.colors.slot")
                .comment("Color of the slot outline when 'unified' is disabled.")
                .defineInRange("slot", 0x66D9FF, 0, 0xFFFFFF);

        RAINBOW_UNIFIED = builder
                .translation("ae2itemlocator.config.client.colors.rainbowUnified")
                .comment("Cycle the unified color through the rainbow instead of using a fixed value.")
                .define("rainbowUnified", false);

        RAINBOW_BEAM = builder
                .translation("ae2itemlocator.config.client.colors.rainbowBeam")
                .comment("Cycle the beam color through the rainbow when 'unified' is disabled.")
                .define("rainbowBeam", false);

        RAINBOW_DRIVE = builder
                .translation("ae2itemlocator.config.client.colors.rainbowDrive")
                .comment("Cycle the drive color through the rainbow when 'unified' is disabled.")
                .define("rainbowDrive", false);

        RAINBOW_SLOT = builder
                .translation("ae2itemlocator.config.client.colors.rainbowSlot")
                .comment("Cycle the slot color through the rainbow when 'unified' is disabled.")
                .define("rainbowSlot", false);

        builder.pop();

        builder.translation("ae2itemlocator.config.client.hud")
                .comment("Position of the on-screen status text, in permille of the screen size.")
                .push("hud");

        HUD_X = builder
                .translation("ae2itemlocator.config.client.hud.x")
                .comment("Horizontal position, 0 = left edge, 1000 = right edge.")
                .defineInRange("x", 8, 0, 1000);

        HUD_Y = builder
                .translation("ae2itemlocator.config.client.hud.y")
                .comment("Vertical position, 0 = top edge, 1000 = bottom edge.")
                .defineInRange("y", 8, 0, 1000);

        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    public static boolean unified() {
        return UNIFIED_COLOR.get();
    }

    public static int beamColor() {
        return resolve(COLOR_BEAM, RAINBOW_BEAM, 0f);
    }

    public static int driveColor() {
        return resolve(COLOR_DRIVE, RAINBOW_DRIVE, 1f / 3f);
    }

    public static int slotColor() {
        return resolve(COLOR_SLOT, RAINBOW_SLOT, 2f / 3f);
    }

    /** Current colour of the rainbow cycle, for the settings screen's swatch preview. */
    public static int rainbowPreview(float phase) {
        return rainbowRgb(phase);
    }

    /**
     * Unified mode wins over the per-part value, and the same rule applies to the rainbow flags:
     * with "one colour" enabled the three parts share one cycling colour, otherwise each part
     * cycles on its own phase so they stay visually distinct.
     */
    private static int resolve(ModConfigSpec.IntValue separate, ModConfigSpec.BooleanValue separateRainbow,
            float phase) {
        boolean isUnified = unified();
        boolean rainbow = isUnified ? RAINBOW_UNIFIED.get() : separateRainbow.get();
        int rgb = rainbow ? rainbowRgb(phase) : (isUnified ? COLOR_UNIFIED.get() : separate.get());
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static final long RAINBOW_PERIOD_MS = 6000L;

    private static int rainbowRgb(float phase) {
        float hue = ((System.currentTimeMillis() % RAINBOW_PERIOD_MS) / (float) RAINBOW_PERIOD_MS + phase) % 1f;
        float scaled = hue * 6f;
        int sector = (int) scaled % 6;
        float f = scaled - (int) scaled;
        float q = 1f - f;

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = 1f; g = f; b = 0f; }
            case 1 -> { r = q; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = f; }
            case 3 -> { r = 0f; g = q; b = 1f; }
            case 4 -> { r = f; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private ClientConfig() {
    }
}
