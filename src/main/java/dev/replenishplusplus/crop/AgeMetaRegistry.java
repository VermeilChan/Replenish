package dev.replenishplusplus.crop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Scans supported crops at startup and precomputes every age-state
 * BlockData variant so the replant queue never needs to clone or
 * mutate BlockData at runtime.
 */
public final class AgeMetaRegistry {

    /** Horizontal faces a cocoa pod can attach to, in ordinal order. */
    public static final List<BlockFace> COCOA_FACES = List.of(
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private final Map<Material, CropInfo> registry;

    public AgeMetaRegistry(Plugin plugin) {
        this.registry = new EnumMap<>(Material.class);
        for (CropType crop : CropType.values()) {
            try {
                register(crop);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING,
                        "Age meta scan skipped for " + crop.material(), error);
            }
        }
    }

    private void register(CropType crop) {
        Material material = crop.material();
        BlockData base = Bukkit.createBlockData(material);
        if (!(base instanceof Ageable)) return;

        int maxAge = ((Ageable) base).getMaximumAge();
        CropInfo info = crop.isCocoa()
                ? buildCocoa(base, maxAge)
                : buildSimple(crop, base, maxAge);

        if (info != null) {
            registry.put(material, info);
        }
    }

    private SimpleCropInfo buildSimple(CropType crop, BlockData base, int maxAge) {
        BlockData[] states = new BlockData[maxAge + 1];
        for (int age = 0; age <= maxAge; age++) {
            BlockData data = base.clone();
            ((Ageable) data).setAge(age);
            states[age] = data;
        }
        boolean farmland = !crop.isNetherWart();
        boolean soulSand = crop.isNetherWart();
        return new SimpleCropInfo(maxAge, farmland, soulSand, states);
    }

    private CocoaCropInfo buildCocoa(BlockData base, int maxAge) {
        if (!(base instanceof Directional)) return null;

        BlockData[][] states = new BlockData[maxAge + 1][COCOA_FACES.size()];
        for (int age = 0; age <= maxAge; age++) {
            for (int face = 0; face < COCOA_FACES.size(); face++) {
                BlockData data = base.clone();
                ((Ageable) data).setAge(age);
                ((Directional) data).setFacing(COCOA_FACES.get(face));
                states[age][face] = data;
            }
        }
        return new CocoaCropInfo(maxAge, COCOA_FACES, states);
    }

    public CropInfo get(Material material) {
        return material == null ? null : registry.get(material);
    }
}