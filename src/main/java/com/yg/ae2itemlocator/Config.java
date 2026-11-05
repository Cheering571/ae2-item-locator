package com.yg.ae2itemlocator;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration. Only ever read from server code paths.
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ALLOW_QUERY;
    public static final ModConfigSpec.IntValue CELLS_PER_TICK;
    public static final ModConfigSpec.IntValue CACHE_SECONDS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("AE2 Item Locator (server)").push("server");

        ALLOW_QUERY = builder
                .comment("Allow clients to ask which physical drive holds a given item.",
                        "Disabling this turns the tooltip section and the highlight off for everyone.")
                .define("allowQuery", true);

        CELLS_PER_TICK = builder
                .comment("How many storage cells are inspected per server tick while an index is being built.",
                        "Lower = smoother, but the first lookup takes longer.")
                .defineInRange("cellsPerTick", 12, 1, 512);

        CACHE_SECONDS = builder
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
