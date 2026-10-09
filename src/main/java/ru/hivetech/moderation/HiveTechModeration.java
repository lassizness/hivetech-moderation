package ru.hivetech.moderation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
    modid = HiveTechModeration.MODID,
    name = HiveTechModeration.NAME,
    version = HiveTechModeration.VERSION,
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "[1.12.2]",
    serverSideOnly = true
)
public final class HiveTechModeration {
    public static final String MODID = "hivetechmoderation";
    public static final String NAME = "HiveTech Moderation";
    public static final String VERSION = "1.0.0";

    public static Logger LOG;

    private static ModerationConfig config;
    private static ModerationCache cache;
    private static ModerationBridge bridge;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG = event.getModLog();

        File configDir = event.getModConfigurationDirectory();
        config = new ModerationConfig(new File(configDir, "hivetech-moderation.cfg"));
        config.load();

        cache = new ModerationCache(new File(configDir, "hivetech-moderation-cache.json"));
        cache.load();

        LOG.info(
            "HiveTech Moderation initialized. serverCode={}, cachedRevision={}, cachedActions={}",
            config.getServerCode(),
            cache.getRevision(),
            cache.activeCount()
        );
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ModerationEvents());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        bridge = new ModerationBridge(event.getServer(), config, cache);
        event.registerServerCommand(new ModerationCommand());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (bridge != null) {
            bridge.start();
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (bridge != null) {
            bridge.stop();
            bridge = null;
        }
    }

    public static ModerationBridge getBridge() {
        return bridge;
    }

    public static ModerationConfig getConfig() {
        return config;
    }

    public static ModerationCache getCache() {
        return cache;
    }
}
