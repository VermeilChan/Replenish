package dev.replenish;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public record SoundEffect(boolean enabled, Sound sound, float volume, float pitch) {

    private static final float MIN_VOLUME = 0.0f;
    private static final float MAX_VOLUME = 1.0f;
    private static final float MIN_PITCH = 0.5f;
    private static final float MAX_PITCH = 2.0f;

    public SoundEffect(boolean enabled, Sound sound, float volume, float pitch) {
        this.enabled = enabled;
        this.sound = sound;
        this.volume = clamp(volume, MIN_VOLUME, MAX_VOLUME);
        this.pitch = clamp(pitch, MIN_PITCH, MAX_PITCH);
    }

    @Override
    public boolean enabled() {
        return enabled && sound != null;
    }

    public void play(Player player) {
        if (!enabled || sound == null || player == null || !player.isOnline()) return;
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;
        player.playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        return Math.min(value, max);
    }
}
