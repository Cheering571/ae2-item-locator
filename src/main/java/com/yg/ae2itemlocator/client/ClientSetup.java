package com.yg.ae2itemlocator.client;

import appeng.client.gui.me.common.MEStorageScreen;
import com.yg.ae2itemlocator.net.ClientBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientSetup {

    public static void init(IEventBus modBus, ModContainer container) {
        ClientBridge.onHello = ClientIndexState::markServerReady;
        ClientBridge.onResult = ClientIndexState::accept;
        ClientBridge.onDriveSlots = DriveSlotState::accept;

        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(ClientSetup::registerKeys);

        NeoForge.EVENT_BUS.addListener(ClientSetup::onScreenKey);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRenderGui);
        NeoForge.EVENT_BUS.addListener(HighlightRenderer::onRenderLevel);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(Keybinds.LOCK);
        event.register(Keybinds.CONFIG);
    }

    private static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof MEStorageScreen<?> screen)) {
            return;
        }
        // Do not steal keys while the terminal search box has focus.
        if (screen.getFocused() instanceof EditBox) {
            return;
        }
        if (event.getKeyCode() != Keybinds.LOCK.getKey().getValue()) {
            return;
        }
        Keybinds.toggle();
        event.setCanceled(true);
    }

    private static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof MEStorageScreen<?>) {
            ClientIndexState.clearHover();
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Leaving a world must not leave a stale highlight behind.
        if (minecraft.level == null) {
            HighlightState.clear();
            ClientIndexState.clearHover();
        }

        if (minecraft.screen == null) {
            while (Keybinds.LOCK.consumeClick()) {
                Keybinds.toggle();
            }
            while (Keybinds.CONFIG.consumeClick()) {
                minecraft.setScreen(new ConfigScreen());
            }
        } else {
            // The screens handle their own keys; drain the queue so nothing fires later.
            while (Keybinds.LOCK.consumeClick()) {
                // no-op
            }
            while (Keybinds.CONFIG.consumeClick()) {
                // no-op
            }
        }

        int seconds = ClientConfig.HIGHLIGHT_SECONDS.get();
        if (seconds > 0 && HighlightState.isLocked()
                && System.currentTimeMillis() - HighlightState.lockedAt() > seconds * 1000L) {
            HighlightState.clear();
        }

        DriveSlotState.tick(minecraft);

        // Background refresh of a locked highlight, independent of whether a terminal is open.
        ClientIndexState.pollLocked();
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (!HighlightState.isLocked()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof HudPositionScreen) {
            return; // the editor draws its own preview
        }

        var graphics = event.getGuiGraphics();
        int x = minecraft.getWindow().getGuiScaledWidth() * ClientConfig.HUD_X.get() / 1000;
        int y = minecraft.getWindow().getGuiScaledHeight() * ClientConfig.HUD_Y.get() / 1000;

        Component text = HighlightState.hasRefs()
                ? Component.translatable("hud.ae2itemlocator.locked",
                        HighlightState.name(), HighlightState.refs().size())
                : Component.translatable("hud.ae2itemlocator.locked.empty", HighlightState.name());
        graphics.drawString(minecraft.font, text, x, y, ClientConfig.driveColor(), true);

        if (minecraft.level != null) {
            long elsewhere = HighlightState.refs().stream()
                    .filter(ref -> !ref.dimension().equals(minecraft.level.dimension()))
                    .count();
            if (elsewhere > 0) {
                Component other = Component.translatable("hud.ae2itemlocator.other_dimension", elsewhere)
                        .withStyle(ChatFormatting.GRAY);
                graphics.drawString(minecraft.font, other, x, y + 10, 0xFFAAAAAA, true);
            }
        }
    }

    private ClientSetup() {
    }
}
