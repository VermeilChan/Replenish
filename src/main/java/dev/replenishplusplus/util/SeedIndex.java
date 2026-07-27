package dev.replenishplusplus.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedIndex {

    private static final int SLOT_NONE     = -1;
    private static final int SLOT_OFFHAND  = -2;
    private static final int STORAGE_SIZE  = 36;

    private static final Map<UUID, Map<Material, Integer>> cacheByPlayer = new ConcurrentHashMap<>();

    private SeedIndex() {}

    public static void invalidate(Player player) {
        if (player != null) {
            cacheByPlayer.remove(player.getUniqueId());
        }
    }

    public static boolean consume(Player player, Material seedMaterial) {
        if (player == null || seedMaterial == null || seedMaterial.isAir() || !seedMaterial.isItem()) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> cache = cacheByPlayer.computeIfAbsent(
                player.getUniqueId(), _ -> new HashMap<>());

        synchronized (cache) {
            Integer slot = cache.get(seedMaterial);
            if (slot != null && slot != SLOT_NONE) {
                if (tryConsume(inventory, cache, seedMaterial, slot)) return true;
            }

            cache.clear();
            cache.putAll(buildIndex(inventory));

            Integer refreshed = cache.get(seedMaterial);
            if (refreshed != null && refreshed != SLOT_NONE) {
                return tryConsume(inventory, cache, seedMaterial, refreshed);
            }

            cache.put(seedMaterial, SLOT_NONE);
            return false;
        }
    }

    private static boolean tryConsume(
            PlayerInventory inventory, Map<Material, Integer> cache, Material material, int slot) {

        ItemStack stack = readSlot(inventory, slot);
        if (stack == null || stack.getType() != material || stack.getAmount() <= 0) {
            return false;
        }

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            writeSlot(inventory, slot, stack);
        } else {
            writeSlot(inventory, slot, null);
            cache.put(material, findNextSlot(inventory, material));
        }
        return true;
    }

    private static ItemStack readSlot(PlayerInventory inventory, int slot) {
        return isOffhand(slot) ? inventory.getItem(EquipmentSlot.OFF_HAND) : inventory.getItem(slot);
    }

    private static void writeSlot(PlayerInventory inventory, int slot, ItemStack stack) {
        if (isOffhand(slot)) {
            inventory.setItem(EquipmentSlot.OFF_HAND, stack);
        } else {
            inventory.setItem(slot, stack);
        }
    }

    private static boolean isOffhand(int slot) {
        return slot == SLOT_OFFHAND;
    }

    private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
        Map<Material, Integer> index = new HashMap<>();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (isUsable(stack)) {
                index.putIfAbsent(stack.getType(), i);
            }
        }
        ItemStack offhand = inventory.getItem(EquipmentSlot.OFF_HAND);
        if (isUsable(offhand)) {
            index.putIfAbsent(offhand.getType(), SLOT_OFFHAND);
        }
        return index;
    }

    private static boolean isUsable(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    private static int findNextSlot(PlayerInventory inventory, Material material) {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material && stack.getAmount() > 0) {
                return i;
            }
        }
        ItemStack offhand = inventory.getItem(EquipmentSlot.OFF_HAND);
        if (offhand.getType() == material && offhand.getAmount() > 0) {
            return SLOT_OFFHAND;
        }
        return SLOT_NONE;
    }
}