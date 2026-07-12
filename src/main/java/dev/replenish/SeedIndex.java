package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SeedIndex {

    private static final int NO_SLOT = -1;
    private static final int OFFHAND_SLOT = -2;
    private static final int STORAGE_SIZE = 36;

    private static final Map<UUID, Map<Material, Integer>> cacheByPlayer = new HashMap<>();

    private SeedIndex() {}

    public static void invalidate(UUID uuid) {
        cacheByPlayer.remove(uuid);
    }

    public static void invalidate(Player player) {
        if (player != null) invalidate(player.getUniqueId());
    }

    public static boolean consume(Player player, Material seedMaterial) {
        if (player == null
                || seedMaterial == null
                || seedMaterial.isAir()
                || !seedMaterial.isItem()) return false;

        PlayerInventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        Map<Material, Integer> playerCache =
                cacheByPlayer.computeIfAbsent(uuid, k -> buildIndex(inventory));

        Integer cachedSlot = playerCache.get(seedMaterial);

        if (cachedSlot != null) {
            if (cachedSlot == NO_SLOT) {
                return false;
            }

            if (tryConsume(inventory, playerCache, seedMaterial, cachedSlot)) {
                return true;
            }
        }

        playerCache.clear();
        playerCache.putAll(buildIndex(inventory));

        Integer newSlot = playerCache.get(seedMaterial);
        if (newSlot != null && newSlot != NO_SLOT) {
            return tryConsume(inventory, playerCache, seedMaterial, newSlot);
        }

        playerCache.put(seedMaterial, NO_SLOT);
        return false;
    }

    private static boolean tryConsume(
            PlayerInventory inventory, Map<Material, Integer> cache, Material material, int slot) {
        ItemStack stack =
                (slot == OFFHAND_SLOT)
                        ? inventory.getItem(EquipmentSlot.OFF_HAND)
                        : inventory.getItem(slot);

        if (stack == null || stack.getType() != material || stack.getAmount() <= 0) {
            return false;
        }

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            if (slot == OFFHAND_SLOT) {
                inventory.setItem(EquipmentSlot.OFF_HAND, stack);
            } else {
                inventory.setItem(slot, stack);
            }
        } else {
            if (slot == OFFHAND_SLOT) {
                inventory.setItem(EquipmentSlot.OFF_HAND, null);
            } else {
                inventory.setItem(slot, null);
            }
            cache.put(material, findNextSlot(inventory, material));
        }
        return true;
    }

    private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
        Map<Material, Integer> index = new HashMap<>();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                index.putIfAbsent(stack.getType(), i);
            }
        }
        ItemStack offhand = inventory.getItem(EquipmentSlot.OFF_HAND);
        if (!offhand.getType().isAir() && offhand.getAmount() > 0) {
            index.putIfAbsent(offhand.getType(), OFFHAND_SLOT);
        }
        return index;
    }

    private static int findNextSlot(PlayerInventory inventory, Material material) {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material && stack.getAmount() > 0) return i;
        }
        ItemStack offhand = inventory.getItem(EquipmentSlot.OFF_HAND);
        if (offhand.getType() == material && offhand.getAmount() > 0) {
            return OFFHAND_SLOT;
        }
        return NO_SLOT;
    }
}
