package dev.replenish.config;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Optional;

/**
 * Reads a SoundEffect from config, resolving both modern namespaced
 * keys and legacy Bukkit enum names.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SoundResolver {

    private SoundResolver() {}

    public static SoundEffect read(
            FileConfiguration config, String key, Sound defaultSound, float defaultPitch) {

        String base = "sounds." + key + ".";
        boolean enabled  = config.getBoolean(base + "enabled", true);
        String rawName   = config.getString(base + "sound", nameOf(defaultSound));
        float volume     = (float) config.getDouble(base + "volume", 1.0);
        float pitch      = (float) config.getDouble(base + "pitch", defaultPitch);

        Optional<Sound> resolved = resolve(rawName);
        Sound sound = resolved.orElse(defaultSound);

        if (resolved.isEmpty() && !matchesName(rawName, defaultSound)) {
            Bukkit.getLogger().warning("[Replenish] Unknown sound '" + rawName
                    + "' for sounds." + key + ".sound — falling back to " + nameOf(defaultSound));
        }

        return new SoundEffect(enabled, sound, volume, pitch);
    }

    private static Optional<Sound> resolve(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String name = input.trim();

        Sound byKey = resolveByKey(name);
        if (byKey != null) return Optional.of(byKey);

        Sound byLegacy = resolveByLegacyName(name);
        return byLegacy != null ? Optional.of(byLegacy) : Optional.empty();
    }

    private static Sound resolveByKey(String name) {
        try {
            String cleaned = name.toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(cleaned);
            if (key == null && !cleaned.contains(":")) {
                key = NamespacedKey.minecraft(cleaned);
            }
            return key == null ? null : Registry.SOUNDS.get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Sound resolveByLegacyName(String name) {
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key == null) continue;
            String legacy = key.getKey().replace('.', '_').toUpperCase(Locale.ROOT);
            if (legacy.equalsIgnoreCase(name)) return sound;
        }
        return null;
    }

    private static boolean matchesName(String input, Sound sound) {
        return input != null && input.trim().equalsIgnoreCase(nameOf(sound));
    }

    private static String nameOf(Sound sound) {
        NamespacedKey key = Registry.SOUNDS.getKey(sound);
        return key != null ? key.getKey() : "unknown";
    }
}