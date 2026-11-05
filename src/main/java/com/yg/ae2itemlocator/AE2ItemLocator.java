package com.yg.ae2itemlocator;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yg.ae2itemlocator.net.HelloPayload;
import com.yg.ae2itemlocator.net.Network;
import com.yg.ae2itemlocator.server.IndexManager;

/**
 * Entry point.
 *
 * <p>This mod deliberately registers no blocks and no items. It only decorates the existing AE2
 * terminals: the item tooltip gains a "which drive holds this" section, and a keybind locks a
 * world-space highlight onto the drives that were reported by the server.
 */
@Mod(AE2ItemLocator.MODID)
public final class AE2ItemLocator {
    public static final String MODID = "ae2itemlocator";
    public static final Logger LOG = LoggerFactory.getLogger("AE2 Item Locator");

    public AE2ItemLocator(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(Network::register);

        NeoForge.EVENT_BUS.addListener(AE2ItemLocator::onServerTick);
        NeoForge.EVENT_BUS.addListener(AE2ItemLocator::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(AE2ItemLocator::onServerStopping);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Client-only classes are referenced exclusively from inside this branch, so a
            // dedicated server never tries to load them.
            com.yg.ae2itemlocator.client.ClientSetup.init(modBus, container);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        IndexManager.tick();
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new HelloPayload());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        IndexManager.clear();
    }
}
