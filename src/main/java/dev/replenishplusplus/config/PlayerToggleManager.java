package dev.replenishplusplus.config;

import dev.replenishplusplus.ReplenishPlusPlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerToggleManager {

    private final ReplenishPlusPlus plugin;
    private final File file;
    private FileConfiguration config;
    private final ConcurrentHashMap<UUID, Boolean> toggles = new ConcurrentHashMap<>();
    private final Object configLock = new Object();

    public PlayerToggleManager(ReplenishPlusPlus plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("Could not create data folder: " + plugin.getDataFolder());
                }
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("players.yml already existed when creating it");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create players.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                toggles.put(uuid, config.getBoolean(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean isEnabled(Player player) {
        return toggles.getOrDefault(player.getUniqueId(), true);
    }

    public boolean toggle(Player player) {
        boolean nowEnabled = !isEnabled(player);
        toggles.put(player.getUniqueId(), nowEnabled);
        saveAsync(player.getUniqueId(), nowEnabled);
        return nowEnabled;
    }

    private void saveAsync(UUID uuid, boolean enabled) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, _ -> {
            synchronized (configLock) {
                config.set(uuid.toString(), enabled);
                try {
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
                }
            }
        });
    }
}