package com.yg.ae2itemlocator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * A bare R/G/B slider drawn in AE2's palette (no vanilla widget background).
 */
public final class ColorSlider extends AbstractSliderButton {

    private final int channel;
    private final IntConsumer onChange;
    private boolean silent;

    public ColorSlider(int x, int y, int width, int height, int value, int channel, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), value / 255.0);
        this.channel = channel;
        this.onChange = onChange;
        updateMessage();
    }

    public int channelValue() {
        return (int) Math.round(this.value * 255.0);
    }

    public void setChannelValue(int newValue) {
        silent = true;
        this.value = Math.max(0, Math.min(255, newValue)) / 255.0;
        silent = false;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(String.valueOf(channelValue())));
    }

    @Override
    protected void applyValue() {
        if (!silent) {
            onChange.accept(channelValue());
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // AE2-ish flat track
        graphics.fill(x, y, x + w, y + h, 0xFF1E2A31);
        graphics.fill(x, y, x + w, y + 1, 0xFF3C4F5A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF3C4F5A);
        graphics.fill(x, y, x + 1, y + h, 0xFF3C4F5A);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF3C4F5A);

        int fillWidth = (int) (w * this.value);
        graphics.fill(x + 1, y + 1, x + Math.max(1, fillWidth - 1), y + h - 1, channelColor() | 0xFF000000);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawCenteredString(font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private int channelColor() {
        return switch (channel) {
            case 0 -> 0xFF5555;
            case 1 -> 0x55FF55;
            default -> 0x5599FF;
        };
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
    }
}
