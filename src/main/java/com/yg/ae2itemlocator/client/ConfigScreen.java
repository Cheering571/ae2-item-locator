package com.yg.ae2itemlocator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Colour and HUD-position settings.
 *
 * <p>Flat AE2 palette: one panel, two clearly separated sections (highlight colours, on-screen
 * text), and custom flat buttons instead of the vanilla widget look.
 */
public final class ConfigScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 220;

    private static final int PANEL_BG = 0xFF1B262E;
    private static final int PANEL_BORDER = 0xFF3A4F5A;
    private static final int SECTION_BG = 0xFF141E24;
    private static final int SECTION_BORDER = 0xFF2C3D47;
    private static final int TITLE_COLOR = 0xFFE6EEF2;
    private static final int LABEL_COLOR = 0xFFC8D6DD;

    private int px;
    private int py;

    private final List<ColorRow> rows = new ArrayList<>();

    public ConfigScreen() {
        super(Component.translatable("gui.ae2itemlocator.config"));
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        rows.clear();

        addRenderableWidget(new AeButton(px + 10, py + 36, PANEL_W - 20, 16, unifiedLabel(), button -> {
            ClientConfig.UNIFIED_COLOR.set(!ClientConfig.unified());
            // Recreate the screen instead of mutating the widget list from inside a button
            // callback (the screen is iterating its children at that point).
            if (minecraft != null) {
                minecraft.setScreen(new ConfigScreen());
            }
        }));

        if (ClientConfig.unified()) {
            rows.add(new ColorRow(ClientConfig.COLOR_UNIFIED, "gui.ae2itemlocator.color.unified", py + 58));
        } else {
            rows.add(new ColorRow(ClientConfig.COLOR_BEAM, "gui.ae2itemlocator.color.beam", py + 58));
            rows.add(new ColorRow(ClientConfig.COLOR_DRIVE, "gui.ae2itemlocator.color.drive", py + 88));
            rows.add(new ColorRow(ClientConfig.COLOR_SLOT, "gui.ae2itemlocator.color.slot", py + 118));
        }

        addRenderableWidget(new AeButton(px + 10, py + 166, PANEL_W - 20, 16,
                Component.translatable("gui.ae2itemlocator.hud_position"), button -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new HudPositionScreen(this));
                    }
                }));

        addRenderableWidget(new AeButton(px + 10, py + 196, PANEL_W - 20, 18,
                Component.translatable("gui.ae2itemlocator.done"), button -> onClose()));
    }

    private Component unifiedLabel() {
        return Component.translatable("gui.ae2itemlocator.unified")
                .append(": ")
                .append(Component.translatable(ClientConfig.unified()
                        ? "gui.ae2itemlocator.on"
                        : "gui.ae2itemlocator.off"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        // panel
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, PANEL_BG);
        graphics.fill(px, py, px + PANEL_W, py + 1, PANEL_BORDER);
        graphics.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, PANEL_BORDER);
        graphics.fill(px, py, px + 1, py + PANEL_H, PANEL_BORDER);
        graphics.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, PANEL_BORDER);

        section(graphics, px + 6, py + 20, PANEL_W - 12, 128);
        section(graphics, px + 6, py + 152, PANEL_W - 12, 40);
    }

    private static void section(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, SECTION_BG);
        graphics.fill(x, y, x + w, y + 1, SECTION_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, SECTION_BORDER);
        graphics.fill(x, y, x + 1, y + h, SECTION_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, SECTION_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null) {
            return;
        }
        var font = minecraft.font;

        graphics.drawString(font, title, px + 10, py + 7, TITLE_COLOR, false);

        // section headers
        graphics.drawString(font, Component.translatable("gui.ae2itemlocator.section.colors"),
                px + 12, py + 24, LABEL_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.ae2itemlocator.section.hud"),
                px + 12, py + 156, LABEL_COLOR, false);

        for (ColorRow row : rows) {
            graphics.drawString(font, Component.translatable(row.labelKey), px + 12, row.y, LABEL_COLOR, false);

            int rgb = row.config.get() & 0xFFFFFF;
            int swatchX = px + 96;
            graphics.fill(swatchX, row.y - 2, swatchX + 12, row.y + 10, 0xFF000000 | rgb);
            graphics.fill(swatchX - 1, row.y - 3, swatchX + 13, row.y - 2, SECTION_BORDER);
            graphics.fill(swatchX - 1, row.y + 10, swatchX + 13, row.y + 11, SECTION_BORDER);
            graphics.fill(swatchX - 1, row.y - 3, swatchX, row.y + 11, SECTION_BORDER);
            graphics.fill(swatchX + 12, row.y - 3, swatchX + 13, row.y + 11, SECTION_BORDER);
        }
    }

    @Override
    public void onClose() {
        ClientConfig.SPEC.save();
        super.onClose();
    }

    private final class ColorRow {
        private final ModConfigSpec.ConfigValue<Integer> config;
        private final String labelKey;
        private final int y;
        private final ColorSlider red;
        private final ColorSlider green;
        private final ColorSlider blue;
        private final EditBox hex;
        private boolean syncing;

        ColorRow(ModConfigSpec.ConfigValue<Integer> config, String labelKey, int y) {
            this.config = config;
            this.labelKey = labelKey;
            this.y = y;

            int rgb = config.get() & 0xFFFFFF;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            this.red = new ColorSlider(px + 10, y + 12, 62, 10, r, 0, v -> channelChanged());
            this.green = new ColorSlider(px + 78, y + 12, 62, 10, g, 1, v -> channelChanged());
            this.blue = new ColorSlider(px + 146, y + 12, 62, 10, b, 2, v -> channelChanged());
            addRenderableWidget(red);
            addRenderableWidget(green);
            addRenderableWidget(blue);

            this.hex = new EditBox(font, px + 114, y - 3, 56, 14, Component.empty());
            this.hex.setMaxLength(6);
            this.hex.setValue(String.format("%06X", rgb));
            this.hex.setResponder(this::hexChanged);
            addRenderableWidget(hex);
        }

        private void channelChanged() {
            if (syncing) {
                return;
            }
            syncing = true;
            int rgb = (red.channelValue() << 16) | (green.channelValue() << 8) | blue.channelValue();
            config.set(rgb);
            hex.setValue(String.format("%06X", rgb));
            syncing = false;
        }

        private void hexChanged(String text) {
            if (syncing || text == null || text.length() != 6) {
                return;
            }
            int rgb;
            try {
                rgb = Integer.parseInt(text, 16);
            } catch (NumberFormatException e) {
                return;
            }
            syncing = true;
            config.set(rgb & 0xFFFFFF);
            red.setChannelValue((rgb >> 16) & 0xFF);
            green.setChannelValue((rgb >> 8) & 0xFF);
            blue.setChannelValue(rgb & 0xFF);
            syncing = false;
        }
    }
}
