package com.yg.ae2itemlocator;

import com.yg.ae2itemlocator.net.HelloPayload;
import com.yg.ae2itemlocator.net.Network;
import com.yg.ae2itemlocator.server.IndexManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public AE2ItemLocator() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        Network.register();

        MinecraftForge.EVENT_BUS.addListener(AE2ItemLocator::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(AE2ItemLocator::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(AE2ItemLocator::onServerStopping);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Client-only classes are referenced exclusively from inside this branch, so a
            // dedicated server never tries to load them.
            com.yg.ae2itemlocator.client.ClientSetup.init(modBus);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            IndexManager.tick();
        }
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Network.toPlayer(player, new HelloPayload());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        IndexManager.clear();
    }
}
