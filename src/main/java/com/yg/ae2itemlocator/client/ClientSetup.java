package com.yg.ae2itemlocator.client;

import appeng.client.gui.me.common.MEStorageScreen;
import com.yg.ae2itemlocator.net.ClientBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class ClientSetup {

    public static void init(IEventBus modBus) {
        ClientBridge.onHello = ClientIndexState::markServerReady;
        ClientBridge.onResult = ClientIndexState::accept;
        ClientBridge.onDriveSlots = DriveSlotState::accept;

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(ClientSetup::registerKeys);

        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onScreenKey);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onScreenClosing);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(HighlightRenderer::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(EcoStorageOverlay::onRenderScreen);
        MinecraftForge.EVENT_BUS.addListener(EcoStorageOverlay::onContainerForeground);
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

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
