package dev.replenishplusplus.util;

import dev.replenishplusplus.config.MessageStyle;
import dev.replenishplusplus.config.SoundEffect;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.Map;

public final class DropPickupManager {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private DropPickupManager() {}

    public static void giveOrDrop(
            Player player,
            Location dropLocation,
            Collection<ItemStack> drops,
            String inventoryFullMessage,
            SoundEffect pickupSound,
            SoundEffect inventoryFullSound,
            MessageStyle messageStyle) {

        if (player == null || !player.isOnline() || dropLocation == null || drops == null || drops.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        Location fallback = player.getLocation();
        boolean anyAdded = false;
        boolean anyDropped = false;

        for (ItemStack stack : drops) {
            if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) continue;

            try {
                ItemStack toGive = stack.clone();
                int originalAmount = toGive.getAmount();
                Map<Integer, ItemStack> leftovers = inventory.addItem(toGive);

                if (leftovers.isEmpty()) {
                    anyAdded = true;
                } else {
                    int leftoverAmount = 0;
                    for (ItemStack leftover : leftovers.values()) {
                        if (leftover == null || leftover.getAmount() <= 0) continue;
                        leftoverAmount += leftover.getAmount();
                        dropSafely(dropLocation, fallback, leftover);
                        anyDropped = true;
                    }
                    if (leftoverAmount < originalAmount) anyAdded = true;
                }
            } catch (Exception e) {
                dropSafely(dropLocation, fallback, stack);
                anyDropped = true;
            }
        }

        if (anyDropped) {
            sendMessage(player, inventoryFullMessage, messageStyle);
            if (inventoryFullSound != null) inventoryFullSound.play(player);
        }
        if (anyAdded && pickupSound != null) {
            pickupSound.play(player);
        }
    }

    private static void sendMessage(Player player, String message, MessageStyle style) {
        if (style == MessageStyle.NONE) return;
        if (style == MessageStyle.ACTION_BAR) {
            player.sendActionBar(MINI_MESSAGE.deserialize(message));
        } else {
            player.sendMessage(MINI_MESSAGE.deserialize(message));
        }
    }

    private static void dropSafely(Location preferred, Location fallback, ItemStack stack) {
        if (preferred.getWorld() != null && LocationUtil.isChunkLoadedAround(preferred)) {
            preferred.getWorld().dropItemNaturally(preferred, stack);
        } else if (fallback.getWorld() != null) {
            fallback.getWorld().dropItemNaturally(fallback, stack);
        }
    }
}