package dev.replenish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ReplenishPlugin extends JavaPlugin {

    private static final int DEFAULT_REPLANT_DELAY_TICKS = 1;
    private static final int DEFAULT_MAX_REPLANTS = 4096;
    private static final int MIN_REPLANTS_PER_TICK = 256;

    private final AtomicReference<ConfigCache> configCacheRef =
            new AtomicReference<>(ConfigCache.getDefault());
    private volatile ReplantQueue replantQueue;
    private AgeMetaRegistry ageMetaRegistry;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ageMetaRegistry = new AgeMetaRegistry(this);

        reloadLocalConfig();

        ConfigCache cfg = getConfigCache();
        int supportedCrops = 0;
        for (Boolean enabled : cfg.cropEnabled.values()) {
            if (enabled) supportedCrops++;
        }
        getLogger().info("Loaded successfully.");
        getLogger().info("Supported crops: " + supportedCrops);
        getLogger().info("Queue size: " + cfg.maxReplantsPerTick);
        getLogger().info("Delay: " + cfg.replantDelayTicks + " tick");

        getLogger().info("Running version: v" + getDescription().getVersion());

        boolean checkUpdates = getConfig().getBoolean("checkUpdates", true);
        updateChecker = new UpdateChecker(this, checkUpdates);
        updateChecker.check();

        getServer()
                .getPluginManager()
                .registerEvents(new ReplenishListener(this, ageMetaRegistry), this);

        PluginCommand command = getCommand("replenish");
        if (command != null) {
            ReplenishCommand replenishCommand = new ReplenishCommand(this);
            command.setExecutor(replenishCommand);
            command.setTabCompleter(replenishCommand);
        }
    }

    @Override
    public void onDisable() {
        if (replantQueue != null) replantQueue.stop();
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        boolean regenerated = false;
        if (!config.contains("config-version") || config.getInt("config-version", 1) < 4) {
            getLogger().warning("Config version mismatch. Generating missing values...");
            config.options().copyDefaults(true);
            config.set("config-version", 3);
            regenerated = true;
        }

        int delayTicks =
                Math.max(1, config.getInt("replantDelayTicks", DEFAULT_REPLANT_DELAY_TICKS));
        int maxPerTick =
                Math.max(
                        MIN_REPLANTS_PER_TICK,
                        config.getInt("maxReplantsPerTick", DEFAULT_MAX_REPLANTS));

        ConfigCache newCache = ConfigCache.from(config, delayTicks, maxPerTick);
        configCacheRef.set(newCache);

        if (replantQueue != null) {
            int pending = replantQueue.getPendingCount();
            if (pending > 0) {
                getLogger()
                        .warning(
                                "Discarded "
                                        + pending
                                        + " pending replants during config reload (queue processes"
                                        + " in 1 tick)");
            }
            replantQueue.stop();
        }
        replantQueue = new ReplantQueue(this, maxPerTick, ageMetaRegistry);
        replantQueue.start();

        if (regenerated) {
            saveConfig();
            getLogger().info("Config updated.");
        }
    }

    public boolean isEnabledGlobally() {
        return getConfigCache().enabled;
    }

    public void setGloballyEnabled(boolean enabled) {
        ConfigCache current = getConfigCache();
        ConfigCache newCache =
                new ConfigCache(
                        enabled,
                        current.requirePlayerSeed,
                        current.directPickup,
                        current.replantDelayTicks,
                        current.maxReplantsPerTick,
                        current.cropEnabled,
                        current.msgInventoryFull,
                        current.msgRequiresTool,
                        current.msgNeedSeed);
        configCacheRef.set(newCache);
    }

    public boolean isCropEnabled(Material crop) {
        return crop != null && getConfigCache().cropEnabled.getOrDefault(crop, true);
    }

    public ConfigCache getConfigCache() {
        return configCacheRef.get();
    }

    public void enqueueReplant(
            Block block,
            Material plantMaterial,
            int delayTicks,
            int targetAge,
            BlockFace cocoaFacingDirection) {
        ReplantQueue queue = this.replantQueue;
        if (queue != null)
            queue.enqueue(block, plantMaterial, delayTicks, targetAge, cocoaFacingDirection);
    }

    public static final class ConfigCache {
        final boolean enabled;
        final boolean requirePlayerSeed;
        final boolean directPickup;
        final int replantDelayTicks;
        final int maxReplantsPerTick;
        final Map<Material, Boolean> cropEnabled;

        final String msgInventoryFull;
        final String msgRequiresTool;
        final String msgNeedSeed;

        private ConfigCache(
                boolean enabled,
                boolean requirePlayerSeed,
                boolean directPickup,
                int replantDelayTicks,
                int maxReplantsPerTick,
                Map<Material, Boolean> cropEnabled,
                String msgInventoryFull,
                String msgRequiresTool,
                String msgNeedSeed) {
            this.enabled = enabled;
            this.requirePlayerSeed = requirePlayerSeed;
            this.directPickup = directPickup;
            this.replantDelayTicks = replantDelayTicks;
            this.maxReplantsPerTick = maxReplantsPerTick;
            this.cropEnabled = cropEnabled;
            this.msgInventoryFull = msgInventoryFull;
            this.msgRequiresTool = msgRequiresTool;
            this.msgNeedSeed = msgNeedSeed;
        }

        static ConfigCache getDefault() {
            return new ConfigCache(
                    true,
                    true,
                    true,
                    DEFAULT_REPLANT_DELAY_TICKS,
                    DEFAULT_MAX_REPLANTS,
                    defaultCrops(),
                    "&8[&eReplenish&8] &8» &7Your inventory is full! Items dropped on the ground.",
                    "&8[&eReplenish&8] &8» &e{crop} &7requires &e{tool}&7.",
                    "&8[&eReplenish&8] &8» &cNeed 1 &e{seed}&c.");
        }

        static ConfigCache from(FileConfiguration config, int delayTicks, int maxPerTick) {
            return new ConfigCache(
                    config.getBoolean("enabled", true),
                    config.getBoolean("requirePlayerSeed", true),
                    config.getBoolean("directPickup", true),
                    delayTicks,
                    maxPerTick,
                    readCrops(config),
                    config.getString(
                            "messages.inventory-full",
                            "&8[&eReplenish&8] &8» &7Your inventory is full! Items dropped on the"
                                    + " ground."),
                    config.getString(
                            "messages.requires-tool",
                            "&8[&eReplenish&8] &8» &e{crop} &7requires &e{tool}&7."),
                    config.getString(
                            "messages.need-seed", "&8[&eReplenish&8] &8» &cNeed 1 &e{seed}&c."));
        }

        private static Map<Material, Boolean> readCrops(FileConfiguration config) {
            Map<Material, Boolean> map = defaultCrops();
            map.put(Material.WHEAT, config.getBoolean("crops.wheat", true));
            map.put(Material.CARROTS, config.getBoolean("crops.carrots", true));
            map.put(Material.POTATOES, config.getBoolean("crops.potatoes", true));
            map.put(Material.NETHER_WART, config.getBoolean("crops.nether_wart", true));
            map.put(Material.COCOA, config.getBoolean("crops.cocoa", true));
            map.put(Material.BEETROOTS, config.getBoolean("crops.beetroots", true));
            return map;
        }

        private static Map<Material, Boolean> defaultCrops() {
            Map<Material, Boolean> map = new EnumMap<>(Material.class);
            map.put(Material.WHEAT, true);
            map.put(Material.CARROTS, true);
            map.put(Material.POTATOES, true);
            map.put(Material.NETHER_WART, true);
            map.put(Material.COCOA, true);
            map.put(Material.BEETROOTS, true);
            return map;
        }
    }
}
