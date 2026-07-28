package dev.replenishplusplus.config;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public record SoundEffect(boolean enabled, Sound sound, float volume, float pitch) {

    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 1.0f;
    private static final float MIN_PITCH  = 0.5f;
    private static final float MAX_PITCH  = 2.0f;

    public SoundEffect {
        volume = clamp(volume, MIN_VOLUME, MAX_VOLUME);
        pitch  = clamp(pitch,  MIN_PITCH,  MAX_PITCH);
    }

    @Override
    public boolean enabled() {
        return enabled && sound != null;
    }

    public void play(Player player) {
        if (!enabled() || player == null || !player.isOnline()) return;
        Location location = player.getLocation();
        if (location.getWorld() == null) return;
        player.playSound(location, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        if (value < min) return min;
        return Math.min(value, max);
    }
}