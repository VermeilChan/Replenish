package dev.replenishplusplus;

import dev.replenishplusplus.command.ReplenishPlusPlusCommand;
import dev.replenishplusplus.config.ConfigCache;
import dev.replenishplusplus.config.Messages;
import dev.replenishplusplus.config.PlayerToggleManager;
import dev.replenishplusplus.crop.AgeMetaRegistry;
import dev.replenishplusplus.crop.CropType;
import dev.replenishplusplus.listener.ReplenishPlusPlusListener;
import dev.replenishplusplus.queue.QueueStats;
import dev.replenishplusplus.queue.ReplantQueue;
import dev.replenishplusplus.update.UpdateChecker;
import dev.replenishplusplus.update.UpdateNotificationListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class ReplenishPlusPlus extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final int DEFAULT_REPLANT_DELAY_TICKS = 1;
    private static final int DEFAULT_MAX_REPLANTS = 1024;
    private static final int DEFAULT_MAX_QUEUED   = 4096;
    private static final int MIN_REPLANTS_PER_TICK = 256;
    private static final int MIN_QUEUED           = 256;

    private final AtomicReference<ConfigCache> configCacheRef =
            new AtomicReference<>(ConfigCache.defaults());

    private AgeMetaRegistry ageMetaRegistry;
    private volatile ReplantQueue replantQueue;
    private UpdateChecker updateChecker;
    private PlayerToggleManager playerToggleManager;
    private ConsoleCommandSender console;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ageMetaRegistry = new AgeMetaRegistry(this);
        playerToggleManager = new PlayerToggleManager(this);
        console = Bukkit.getConsoleSender();
        reloadLocalConfig();

        ConfigCache config = getConfigCache();
        long enabledCrops = config.cropEnabled().values().stream().filter(Boolean::booleanValue).count();

        sendConsole("Loaded successfully.");
        sendConsole("Supported crops: <white>" + enabledCrops);
        sendConsole("Replants per tick: <white>" + config.maxReplantsPerTick());
        sendConsole("Queue capacity: <white>" + config.maxReplantsQueued());
        sendConsole("Delay: <white>" + config.replantDelayTicks() + " tick");
        sendConsole("Running version: <white>v" + getPluginMeta().getVersion());

        updateChecker = new UpdateChecker(this, getConfig().getBoolean("checkUpdates", true));
        updateChecker.check();

        getServer().getPluginManager().registerEvents(new ReplenishPlusPlusListener(this, ageMetaRegistry), this);
        getServer().getPluginManager().registerEvents(new UpdateNotificationListener(this), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> new ReplenishPlusPlusCommand(this).register(event.registrar()));
    }

    @Override
    public void onDisable() {
        if (replantQueue != null) {
            replantQueue.flush();
            replantQueue.stop();
        }
    }

    public void reloadLocalConfig() {
        try {
            reloadConfig();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Config reload failed - keeping previous settings", e);
            return;
        }

        FileConfiguration config = getConfig();

        int rawDelay   = config.getInt("replantDelayTicks", DEFAULT_REPLANT_DELAY_TICKS);
        int rawPerTick = config.getInt("maxReplantsPerTick", DEFAULT_MAX_REPLANTS);
        int rawQueued  = config.getInt("maxReplantsQueued", DEFAULT_MAX_QUEUED);

        if (rawDelay < 1) {
            getLogger().warning("[Replenish] replantDelayTicks was " + rawDelay + ", clamped to 1");
        }
        if (rawPerTick < MIN_REPLANTS_PER_TICK) {
            getLogger().warning("[Replenish] maxReplantsPerTick was " + rawPerTick + ", raised to minimum " + MIN_REPLANTS_PER_TICK);
        }
        if (rawQueued < MIN_QUEUED) {
            getLogger().warning("[Replenish] maxReplantsQueued was " + rawQueued + ", raised to minimum " + MIN_QUEUED);
        }

        int delayTicks = Math.max(1, rawDelay);
        int maxPerTick = Math.max(MIN_REPLANTS_PER_TICK, rawPerTick);
        int maxQueued  = Math.max(MIN_QUEUED, rawQueued);

        configCacheRef.set(ConfigCache.from(config, delayTicks, maxPerTick, maxQueued));

        restartQueue(maxPerTick, maxQueued);
    }

    private void restartQueue(int maxPerTick, int maxQueued) {
        ReplantQueue oldQueue = replantQueue;
        ReplantQueue newQueue = new ReplantQueue(this, maxPerTick, maxQueued, ageMetaRegistry);
        newQueue.start();
        replantQueue = newQueue;

        if (oldQueue != null) {
            int pending = oldQueue.pendingCount();
            if (pending > 0) {
                int flushed = oldQueue.flush();
                sendConsole("<yellow>Flushed " + flushed + "/" + pending + " pending replants before queue restart.");
            }
            oldQueue.stop();
        }
    }

    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public PlayerToggleManager getPlayerToggleManager() { return playerToggleManager; }

    public ConfigCache getConfigCache() { return configCacheRef.get(); }
    public boolean isEnabledGlobally() { return getConfigCache().enabled(); }

    public void setGloballyEnabled(boolean enabled) {
        configCacheRef.updateAndGet(current -> current.withEnabled(enabled));
    }

    public boolean isCropEnabled(CropType crop) {
        return getConfigCache().isCropEnabled(crop);
    }

    public void enqueueReplant(
            Block block, Material material, int delayTicks, int targetAge,
            BlockFace cocoaFacing, UUID playerId, boolean seedConsumed) {
        ReplantQueue queue = this.replantQueue;
        if (queue != null) {
            queue.enqueue(block.getWorld(), block.getX(), block.getY(), block.getZ(), material, delayTicks, targetAge, cocoaFacing, playerId, seedConsumed);
        }
    }

    public QueueStats getQueueStats() {
        ReplantQueue queue = this.replantQueue;
        return queue != null ? queue.getStats() : new QueueStats(0, 0, 0);
    }

    private void sendConsole(String message) {
        if (console != null) {
            console.sendMessage(MINI_MESSAGE.deserialize(Messages.PREFIX + message));
        }
    }
}