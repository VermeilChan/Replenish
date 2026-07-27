package dev.replenishplusplus.config;

import dev.replenishplusplus.crop.CropType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

public record ConfigCache(
        boolean enabled,
        boolean requirePlayerSeed,
        boolean directPickup,
        int replantDelayTicks,
        int maxReplantsPerTick,
        int maxReplantsQueued,
        Map<Material, Boolean> cropEnabled,
        String inventoryFullMessage,
        String requiresToolMessage,
        String needSeedMessage,
        SoundEffect pickupSound,
        SoundEffect inventoryFullSound,
        SoundEffect deniedToolSound,
        SoundEffect deniedSeedSound) {

    public ConfigCache {
        cropEnabled = Map.copyOf(cropEnabled);
    }

    public boolean isCropEnabled(Material material) {
        return material != null && cropEnabled.getOrDefault(material, true);
    }

    public boolean isCropEnabled(CropType crop) {
        return cropEnabled.getOrDefault(crop.material(), true);
    }

    public ConfigCache withEnabled(boolean newEnabled) {
        return new ConfigCache(
                newEnabled, requirePlayerSeed, directPickup,
                replantDelayTicks, maxReplantsPerTick, maxReplantsQueued,
                cropEnabled, inventoryFullMessage, requiresToolMessage, needSeedMessage,
                pickupSound, inventoryFullSound, deniedToolSound, deniedSeedSound);
    }

    public static ConfigCache defaults() {
        return new ConfigCache(
                true, true, true, 1, 1024, 4096,
                defaultCropToggles(),
                Messages.INVENTORY_FULL_DEFAULT,
                Messages.REQUIRES_TOOL_DEFAULT,
                Messages.NEED_SEED_DEFAULT,
                new SoundEffect(true, Sound.ENTITY_ITEM_PICKUP,    1.0f, 1.0f),
                new SoundEffect(true, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f),
                new SoundEffect(true, Sound.ENTITY_VILLAGER_NO,    1.0f, 0.5f),
                new SoundEffect(true, Sound.ENTITY_VILLAGER_NO,    1.0f, 0.5f));
    }

    public static ConfigCache from(FileConfiguration config, int delayTicks, int maxPerTick, int maxQueued) {
        return new ConfigCache(
                config.getBoolean("enabled",            true),
                config.getBoolean("requirePlayerSeed",  true),
                config.getBoolean("directPickup",       true),
                delayTicks,
                maxPerTick,
                maxQueued,
                readCrops(config),
                config.getString("messages.inventory-full", Messages.INVENTORY_FULL_DEFAULT),
                config.getString("messages.requires-tool",  Messages.REQUIRES_TOOL_DEFAULT),
                config.getString("messages.need-seed",      Messages.NEED_SEED_DEFAULT),
                SoundResolver.read(config, "pickup",         Sound.ENTITY_ITEM_PICKUP,    1.0f),
                SoundResolver.read(config, "inventory-full", Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f),
                SoundResolver.read(config, "denied-tool",    Sound.ENTITY_VILLAGER_NO,    0.5f),
                SoundResolver.read(config, "denied-seed",    Sound.ENTITY_VILLAGER_NO,    0.5f));
    }

    private static Map<Material, Boolean> readCrops(FileConfiguration config) {
        Map<Material, Boolean> map = defaultCropToggles();
        map.put(Material.WHEAT,       config.getBoolean("crops.wheat",       true));
        map.put(Material.CARROTS,     config.getBoolean("crops.carrots",     true));
        map.put(Material.POTATOES,    config.getBoolean("crops.potatoes",    true));
        map.put(Material.NETHER_WART, config.getBoolean("crops.nether_wart", true));
        map.put(Material.COCOA,       config.getBoolean("crops.cocoa",       true));
        map.put(Material.BEETROOTS,   config.getBoolean("crops.beetroots",   true));
        return map;
    }

    private static Map<Material, Boolean> defaultCropToggles() {
        Map<Material, Boolean> map = new EnumMap<>(Material.class);
        for (CropType crop : CropType.values()) {
            map.put(crop.material(), true);
        }
        return map;
    }
}