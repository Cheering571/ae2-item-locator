package com.yg.ae2itemlocator.client;

import appeng.api.stacks.AEKey;
import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.common.CellRef;
import com.yg.ae2itemlocator.net.DiskResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Builds the extra tooltip lines. Called from the mixin for every network entry that is hovered
 * in an AE2 terminal, so it must stay allocation-light and must never block.
 */
public final class DiskTooltip {

    /** One-shot diagnostic so a player/dev can confirm the mixin actually applied. */
    private static boolean loggedHook;

    public static void appendSources(AEKey key, List<Component> lines) {
        if (!loggedHook) {
            loggedHook = true;
            AE2ItemLocator.LOG.info("[ae2-disk-locator] terminal tooltip hook is active");
        }

        if (!ClientIndexState.serverReady()) {
            return; // server side of the mod is not installed; stay silent
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ClientIndexState.noteHover(key, minecraft.player.containerMenu.containerId);

        DiskResultPayload result = ClientIndexState.get(key);
        if (result == null) {
            lines.add(Component.translatable("tooltip.ae2itemlocator.indexing", 0).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        switch (result.state()) {
            case DiskResultPayload.BUILDING -> lines.add(
                    Component.translatable("tooltip.ae2itemlocator.indexing", result.progress())
                            .withStyle(ChatFormatting.DARK_GRAY));
            case DiskResultPayload.NONE -> lines.add(
                    Component.translatable("tooltip.ae2itemlocator.no_grid")
                            .withStyle(ChatFormatting.DARK_GRAY));
            case DiskResultPayload.READY -> appendReady(result.refs(), lines);
            default -> {
                // unknown state from a newer server: ignore
            }
        }
    }

    private static void appendReady(List<CellRef> refs, List<Component> lines) {
        if (refs.isEmpty()) {
            lines.add(Component.translatable("tooltip.ae2itemlocator.craftable_only").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        lines.add(Component.translatable("tooltip.ae2itemlocator.header", refs.size()).withStyle(ChatFormatting.GRAY));

        int max = Math.max(1, ClientConfig.MAX_SOURCES.get());
        int shown = 0;
        for (CellRef ref : refs) {
            if (shown >= max) {
                break;
            }
            shown++;
            var line = Component.translatable("tooltip.ae2itemlocator.entry",
                    dimensionName(ref.dimension()),
                    ref.pos().getX(),
                    ref.pos().getY(),
                    ref.pos().getZ(),
                    ref.slot()).withStyle(ChatFormatting.DARK_GRAY);
            if (ref.subnet()) {
                line.append(Component.translatable("tooltip.ae2itemlocator.subnet")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(line);
        }

        if (refs.size() > shown) {
            lines.add(Component.translatable("tooltip.ae2itemlocator.more", refs.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        lines.add(Component.translatable("tooltip.ae2itemlocator.hint", Keybinds.LOCK.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Vanilla dimensions get our own keys; everything else is looked up as
     * {@code dimension.<namespace>.<path>}, which is the convention dimension mods use
     * (e.g. Allthemodium ships {@code dimension.allthemodium.mining} = "挖矿维度").
     */
    private static Component dimensionName(ResourceKey<Level> dimension) {
        var id = dimension.location();
        return switch (id.toString()) {
            case "minecraft:overworld" -> Component.translatable("dimension.ae2itemlocator.overworld");
            case "minecraft:the_nether" -> Component.translatable("dimension.ae2itemlocator.nether");
            case "minecraft:the_end" -> Component.translatable("dimension.ae2itemlocator.end");
            default -> Component.translatableWithFallback(
                    "dimension." + id.getNamespace() + "." + id.getPath(),
                    prettify(id.getPath()));
        };
    }

    /** "mining" -> "Mining", "the_beyond" -> "The Beyond" */
    private static String prettify(String path) {
        var out = new StringBuilder(path.length());
        for (String part : path.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private DiskTooltip() {
    }
}
