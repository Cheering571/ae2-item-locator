package com.yg.ae2itemlocator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Flat button in AE2's palette, so the settings screen does not look like a vanilla menu.
 */
public final class AeButton extends Button {

    public AeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        int background = hovered ? 0xFF33454F : 0xFF26343C;
        int border = hovered ? 0xFF74B4CC : 0xFF445A66;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        graphics.fill(x, y, x + w, y + h, background);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);

        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, getMessage(), x + w / 2, y + (h - 8) / 2,
                active ? 0xFFE6EEF2 : 0xFF7F8C92);
    }
}
