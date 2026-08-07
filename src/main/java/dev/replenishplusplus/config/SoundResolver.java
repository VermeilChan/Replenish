package dev.replenishplusplus.config;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("UnstableApiUsage")
public final class SoundResolver {

    private static final Map<String, Sound> EXACT = new HashMap<>();
    private static final Map<String, Sound> ALIAS = new HashMap<>();
    private static volatile boolean loaded = false;

    private SoundResolver() {}

    public static SoundEffect read(
            FileConfiguration config, String key, Sound defaultSound, float defaultPitch) {

        String base = "sounds." + key + ".";
        boolean enabled = config.getBoolean(base + "enabled", true);
        String rawName = config.getString(base + "sound", nameOf(defaultSound));
        float volume = (float) config.getDouble(base + "volume", 1.0);
        float pitch = (float) config.getDouble(base + "pitch", defaultPitch);

        Optional<Sound> resolved = resolve(rawName);
        Sound sound = resolved.orElse(defaultSound);

        if (resolved.isEmpty() && !matchesName(rawName, defaultSound)) {
            Bukkit.getLogger().warning("[Replenish] Unknown sound '" + rawName + "' for sounds." + key + ".sound — falling back to " + nameOf(defaultSound));
        }

        return new SoundEffect(enabled, sound, volume, pitch);
    }

    private static Optional<Sound> resolve(String input) {
        if (input == null || input.isBlank()) return Optional.empty();

        String trimmed = input.trim();
        if (trimmed.indexOf('-') != -1) return Optional.empty();
        if (!trimmed.matches("[A-Za-z0-9._:]+")) return Optional.empty();

        ensureLoaded();

        String lower = trimmed.toLowerCase(Locale.ROOT);

        Sound exact = EXACT.get(lower);
        if (exact != null) return Optional.of(exact);

        Sound alias = ALIAS.get(normalize(lower));
        return alias != null ? Optional.of(alias) : Optional.empty();
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;

        Registry<Sound> registry = Registry.SOUNDS;
        for (Sound sound : registry) {
            NamespacedKey key = registry.getKey(sound);
            if (key == null) continue;

            putExact(key.toString(), sound);
            putExact(key.getKey(), sound);

            putAlias(key.toString(), sound);
            putAlias(key.getKey(), sound);
        }
        loaded = true;
    }

    private static void putExact(String alias, Sound sound) {
        if (alias == null || alias.isBlank()) return;
        EXACT.putIfAbsent(alias.toLowerCase(Locale.ROOT), sound);
    }

    private static void putAlias(String alias, Sound sound) {
        String normalized = normalize(alias);
        if (!normalized.isEmpty()) ALIAS.putIfAbsent(normalized, sound);
    }

    private static String normalize(String input) {
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.:_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static boolean matchesName(String input, Sound sound) {
        return input != null && input.trim().equalsIgnoreCase(nameOf(sound));
    }

    private static String nameOf(Sound sound) {
        NamespacedKey key = Registry.SOUNDS.getKey(sound);
        return key != null ? key.getKey() : "unknown";
    }
}