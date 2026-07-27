package dev.replenish;

import dev.replenish.command.ReplenishCommand;
import dev.replenish.config.ConfigCache;
import dev.replenish.config.Messages;
import dev.replenish.crop.AgeMetaRegistry;
import dev.replenish.crop.CropType;
import dev.replenish.listener.ReplenishListener;
import dev.replenish.queue.ReplantQueue;
import dev.replenish.update.UpdateChecker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicReference;

public final class ReplenishPlugin extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final int DEFAULT_REPLANT_DELAY_TICKS = 1;
    private static final int DEFAULT_MAX_REPLANTS = 1024;
    private static final int MIN_REPLANTS_PER_TICK = 256;
    private static final int CONFIG_VERSION = 6;

    private final AtomicReference<ConfigCache> configCacheRef =
            new AtomicReference<>(ConfigCache.defaults());

    private AgeMetaRegistry ageMetaRegistry;
    private volatile ReplantQueue replantQueue;
    private UpdateChecker updateChecker;
    private ConsoleCommandSender console;

    // --------------------------- Lifecycle ---------------------------

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ageMetaRegistry = new AgeMetaRegistry(this);
        console = Bukkit.getConsoleSender();
        reloadLocalConfig();

        ConfigCache config = getConfigCache();
        long enabledCrops = config.cropEnabled().values().stream()
                .filter(Boolean::booleanValue)
                .count();

        sendConsole("Loaded successfully.");
        sendConsole("Supported crops: <white>" + enabledCrops);
        sendConsole("Queue size: <white>" + config.maxReplantsPerTick());
        sendConsole("Delay: <white>" + config.replantDelayTicks() + " tick");
        sendConsole("Running version: <white>v" + getPluginMeta().getVersion());

        updateChecker = new UpdateChecker(this, getConfig().getBoolean("checkUpdates", true));
        updateChecker.check();

        getServer().getPluginManager()
                .registerEvents(new ReplenishListener(this, ageMetaRegistry), this);
        getServer().getCommandMap().register("replenish", new ReplenishCommand(this));
    }

    @Override
    public void onDisable() {
        if (replantQueue != null) replantQueue.stop();
    }

    // --------------------------- Config ---------------------------

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        boolean regenerated = migrateConfigIfNeeded(config);

        int delayTicks = Math.max(1, config.getInt("replantDelayTicks", DEFAULT_REPLANT_DELAY_TICKS));
        int maxPerTick = Math.max(MIN_REPLANTS_PER_TICK, config.getInt("maxReplantsPerTick", DEFAULT_MAX_REPLANTS));

        configCacheRef.set(ConfigCache.from(config, delayTicks, maxPerTick));
        restartQueue(maxPerTick, regenerated);
    }

    private boolean migrateConfigIfNeeded(FileConfiguration config) {
        int oldVersion = config.getInt("config-version", 1);
        if (config.contains("config-version") && oldVersion >= CONFIG_VERSION) {
            return false;
        }

        sendConsole("<yellow>Your config file is outdated (v" + oldVersion + ").");
        sendConsole("<yellow>Updating to <white>v" + CONFIG_VERSION
                + "<yellow> and adding new default options...");
        sendConsole("<gray>(Don't worry, your existing custom settings are safe!)");

        config.options().copyDefaults(true);
        config.set("config-version", CONFIG_VERSION);
        return true;
    }

    private void restartQueue(int maxPerTick, boolean saveAfterMigration) {
        if (replantQueue != null) {
            int pending = replantQueue.pendingCount();
            if (pending > 0) {
                sendConsole("<yellow>Discarded " + pending
                        + " pending replants during config reload (queue processes in 1 tick)");
            }
            replantQueue.stop();
        }
        replantQueue = new ReplantQueue(this, maxPerTick, ageMetaRegistry);
        replantQueue.start();

        if (saveAfterMigration) {
            saveConfig();
            sendConsole("<green>Config successfully updated and saved!");
        }
    }

    // --------------------------- Public API ---------------------------

    public UpdateChecker getUpdateChecker() { return updateChecker; }

    public ConfigCache getConfigCache() { return configCacheRef.get(); }

    public boolean isEnabledGlobally() { return getConfigCache().enabled(); }

    public void setGloballyEnabled(boolean enabled) {
        configCacheRef.updateAndGet(current -> current.withEnabled(enabled));
    }

    public boolean isCropEnabled(Material crop) {
        return getConfigCache().isCropEnabled(crop);
    }

    public boolean isCropEnabled(CropType crop) {
        return getConfigCache().isCropEnabled(crop);
    }

    public void enqueueReplant(
            Block block, Material material, int delayTicks, int targetAge, BlockFace cocoaFacing) {
        ReplantQueue queue = this.replantQueue;
        if (queue != null) {
            queue.enqueue(block, material, delayTicks, targetAge, cocoaFacing);
        }
    }

    // --------------------------- Console ---------------------------

    private void sendConsole(String message) {
        if (console != null) {
            console.sendMessage(MINI_MESSAGE.deserialize(Messages.PREFIX + message));
        }
    }
}