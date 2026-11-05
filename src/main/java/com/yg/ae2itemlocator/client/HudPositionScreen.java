package com.yg.ae2itemlocator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drag the status text anywhere on screen, Xaero-style.
 */
public final class HudPositionScreen extends Screen {

    private final Screen parent;

    private int px;
    private int py;
    private int boxW;
    private int boxH;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudPositionScreen(Screen parent) {
        super(Component.translatable("gui.ae2itemlocator.hud_position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = clamp(width * ClientConfig.HUD_X.get() / 1000, 0, Math.max(0, width - 4));
        py = clamp(height * ClientConfig.HUD_Y.get() / 1000, 0, Math.max(0, height - 4));
        boxW = font.width(sampleText()) + 4;
        boxH = 10;

        addRenderableWidget(Button.builder(Component.translatable("gui.ae2itemlocator.confirm"), button -> {
            ClientConfig.HUD_X.set(clamp(px * 1000 / Math.max(1, width), 0, 1000));
            ClientConfig.HUD_Y.set(clamp(py * 1000 / Math.max(1, height), 0, 1000));
            ClientConfig.SPEC.save();
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).bounds(width / 2 - 104, height - 32, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.ae2itemlocator.cancel"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    private Component sampleText() {
        if (HighlightState.isLocked()) {
            return Component.translatable("hud.ae2itemlocator.locked",
                    HighlightState.name(), HighlightState.refs().size());
        }
        return Component.translatable("gui.ae2itemlocator.hud_sample");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null) {
            return;
        }
        var font = minecraft.font;
        int color = ClientConfig.driveColor();

        graphics.drawCenteredString(font, Component.translatable("gui.ae2itemlocator.hud_hint"),
                width / 2, 24, 0xFFFFFFFF);

        graphics.fill(px - 2, py - 2, px + boxW + 2, py + boxH + 2, 0x66000000);
        graphics.fill(px - 2, py - 2, px + boxW + 2, py - 1, color);
        graphics.fill(px - 2, py + boxH + 1, px + boxW + 2, py + boxH + 2, color);
        graphics.fill(px - 2, py - 2, px - 1, py + boxH + 2, color);
        graphics.fill(px + boxW + 1, py - 2, px + boxW + 2, py + boxH + 2, color);

        graphics.drawString(font, sampleText(), px, py, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= px - 4 && mouseX <= px + boxW + 4
                && mouseY >= py - 4 && mouseY <= py + boxH + 4) {
            dragging = true;
            dragOffsetX = (int) (mouseX - px);
            dragOffsetY = (int) (mouseY - py);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            px = clamp((int) (mouseX - dragOffsetX), 0, Math.max(0, width - boxW));
            py = clamp((int) (mouseY - dragOffsetY), 0, Math.max(0, height - boxH));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
