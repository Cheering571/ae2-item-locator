package com.yg.ae2itemlocator;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side configuration. Only ever read from server code paths.
 *
 * <p>Every entry carries an explicit translation key so the NeoForge mod-list config screen follows
 * the game language (the key plus {@code .tooltip}); the English comment stays as the fallback for
 * languages we do not ship.
 */
public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_QUERY;
    public static final ForgeConfigSpec.IntValue CELLS_PER_TICK;
    public static final ForgeConfigSpec.IntValue CACHE_SECONDS;

    static {
        var builder = new ForgeConfigSpec.Builder();

        builder.translation("ae2itemlocator.config.server")
                .comment("AE2 Item Locator (server)")
                .push("server");

        ALLOW_QUERY = builder
                .translation("ae2itemlocator.config.server.allowQuery")
                .comment("Allow clients to ask which physical drive holds a given item.",
                        "Disabling this turns the tooltip section and the highlight off for everyone.")
                .define("allowQuery", true);

        CELLS_PER_TICK = builder
                .translation("ae2itemlocator.config.server.cellsPerTick")
                .comment("How many storage cells are inspected per server tick while an index is being built.",
                        "Lower = smoother, but the first lookup takes longer.")
                .defineInRange("cellsPerTick", 12, 1, 512);

        CACHE_SECONDS = builder
                .translation("ae2itemlocator.config.server.cacheSeconds")
                .comment("Upper bound on how long a built index may be used before it is rebuilt.",
                        "Changes are also detected automatically (new items / new drives are picked up",
                        "within about a second), so this is only a safety net. 0 disables the TTL.")
                .defineInRange("cacheSeconds", 10, 0, 3600);

        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
