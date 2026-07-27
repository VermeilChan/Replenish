package dev.replenishplusplus.crop;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

public enum CropType {
    WHEAT       (Material.WHEAT,        Material.WHEAT_SEEDS,    HarvestTool.HOE),
    CARROTS     (Material.CARROTS,      Material.CARROT,         HarvestTool.HOE),
    POTATOES    (Material.POTATOES,     Material.POTATO,         HarvestTool.HOE),
    NETHER_WART (Material.NETHER_WART,  Material.NETHER_WART,    HarvestTool.HOE),
    COCOA       (Material.COCOA,        Material.COCOA_BEANS,    HarvestTool.AXE),
    BEETROOTS   (Material.BEETROOTS,    Material.BEETROOT_SEEDS, HarvestTool.HOE);

    private static final Map<Material, CropType> BY_MATERIAL = new EnumMap<>(Material.class);

    static {
        for (CropType crop : values()) {
            BY_MATERIAL.put(crop.material, crop);
        }
    }

    private final Material material;
    private final Material seed;
    private final HarvestTool requiredTool;

    CropType(Material material, Material seed, HarvestTool requiredTool) {
        this.material = material;
        this.seed = seed;
        this.requiredTool = requiredTool;
    }

    public Material material()       { return material; }
    public Material seed()           { return seed; }
    public HarvestTool requiredTool() { return requiredTool; }

    public boolean isCocoa()     { return this == COCOA; }
    public boolean isNetherWart() { return this == NETHER_WART; }

    
    public static CropType fromMaterial(Material material) {
        return BY_MATERIAL.get(material);
    }
}