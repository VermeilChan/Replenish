package dev.replenish;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.Map;

public final class DropPickupManager {

    private DropPickupManager() {}

    public static void giveToPlayerOrDrop(
            Player player,
            Location dropLocation,
            Collection<ItemStack> drops,
            String inventoryFullMessage,
            SoundEffect pickupSound,
            SoundEffect inventoryFullSound) {
        if (player == null || !player.isOnline() || dropLocation == null || drops == null || drops.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        World world = dropLocation.getWorld();
        boolean anyAdded = false;
        boolean anyDropped = false;
        Location fallbackLoc = player.getLocation();
        World fallbackWorld = fallbackLoc.getWorld();

        for (ItemStack stack : drops) {
            if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) continue;

            try {
                ItemStack itemToGive = stack.clone();
                int originalAmount = itemToGive.getAmount();
                Map<Integer, ItemStack> leftovers = inventory.addItem(itemToGive);

                if (leftovers.isEmpty()) {
                    anyAdded = true;
                } else {
                    int leftoverAmount = 0;
                    for (ItemStack leftover : leftovers.values()) {
                        if (leftover != null && leftover.getAmount() > 0) {
                            leftoverAmount += leftover.getAmount();
                            if (world != null
                                    && world.isChunkLoaded(dropLocation.getBlockX() >> 4, dropLocation.getBlockZ() >> 4)) {
                                world.dropItemNaturally(dropLocation, leftover);
                            } else if (fallbackWorld != null) {
                                fallbackWorld.dropItemNaturally(fallbackLoc, leftover);
                            }
                            anyDropped = true;
                        }
                    }
                    if (leftoverAmount < originalAmount) anyAdded = true;
                }
            } catch (Exception e) {
                if (world != null
                        && world.isChunkLoaded(dropLocation.getBlockX() >> 4, dropLocation.getBlockZ() >> 4)) {
                    world.dropItemNaturally(dropLocation, stack);
                } else if (fallbackWorld != null) {
                    fallbackWorld.dropItemNaturally(fallbackLoc, stack);
                }
                anyDropped = true;
            }
        }

        if (anyDropped) {
            player.sendMessage(ColorUtils.color(inventoryFullMessage));
            if (inventoryFullSound != null) inventoryFullSound.play(player);
        }

        if (anyAdded) {
            if (pickupSound != null) pickupSound.play(player);
        }
    }

    public static Location centeredDropLocation(Location target) {
        if (target == null) return null;

        World world = target.getWorld();
        if (world == null) return null;

        return new Location(
                world, target.getBlockX() + 0.5, target.getBlockY() + 0.5, target.getBlockZ() + 0.5, 0f, 0f);
    }
}
