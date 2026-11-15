package com.aicompanion.build;

import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in structure blueprints.
 */
public class Blueprints {
    private static final Map<String, Blueprint> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put("house", house());
        REGISTRY.put("tower", tower());
        REGISTRY.put("bridge", bridge());
        REGISTRY.put("wall", wall());
        REGISTRY.put("farm", farm());
        REGISTRY.put("shelter", shelter());
        REGISTRY.put("stairs", stairs());
        REGISTRY.put("platform", platform());
        REGISTRY.put("pillar", pillar());
    }

    public static Blueprint get(String name) {
        if (name == null) return null;
        return REGISTRY.get(name.toLowerCase());
    }

    public static java.util.Set<String> names() {
        return REGISTRY.keySet();
    }

    // 7x7 house with door, windows, roof
    private static Blueprint house() {
        var b = Blueprint.builder("house");
        // Floor
        b.box(0, 0, 0, 6, 0, 6, Blocks.OAK_PLANKS);
        // Walls (leave door gap at front center)
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 6; x++) {
                if (!(y <= 2 && x == 3)) b.add(x, y, 0, Blocks.OAK_PLANKS); // front (door at x=3)
                b.add(x, y, 6, Blocks.OAK_PLANKS); // back
            }
            for (int z = 1; z <= 5; z++) {
                b.add(0, y, z, Blocks.OAK_PLANKS);
                b.add(6, y, z, Blocks.OAK_PLANKS);
            }
        }
        // Windows (glass) — replace some wall blocks visually by adding glass inside? Keep simple: add glass panes as blocks on walls
        b.add(1, 2, 0, Blocks.GLASS);
        b.add(5, 2, 0, Blocks.GLASS);
        b.add(0, 2, 3, Blocks.GLASS);
        b.add(6, 2, 3, Blocks.GLASS);
        // Roof (pyramid)
        b.box(0, 4, 0, 6, 4, 6, Blocks.OAK_LOG);
        b.box(1, 5, 1, 5, 5, 5, Blocks.OAK_LOG);
        b.add(2, 6, 2, Blocks.OAK_LOG);
        b.add(3, 6, 2, Blocks.OAK_LOG);
        b.add(4, 6, 2, Blocks.OAK_LOG);
        b.add(2, 6, 3, Blocks.OAK_LOG);
        b.add(3, 6, 3, Blocks.OAK_LOG);
        b.add(4, 6, 3, Blocks.OAK_LOG);
        b.add(2, 6, 4, Blocks.OAK_LOG);
        b.add(3, 6, 4, Blocks.OAK_LOG);
        b.add(4, 6, 4, Blocks.OAK_LOG);
        // Torch inside
        b.add(3, 3, 3, Blocks.TORCH);
        return b.build();
    }

    // 3x3 tower, 10 high
    private static Blueprint tower() {
        var b = Blueprint.builder("tower");
        b.box(0, 0, 0, 2, 0, 2, Blocks.COBBLESTONE);
        b.walls(0, 0, 2, 2, 1, 9, Blocks.COBBLESTONE);
        b.box(0, 10, 0, 2, 10, 2, Blocks.OAK_PLANKS);
        b.walls(0, 0, 2, 2, 11, 12, Blocks.OAK_FENCE);
        b.add(1, 11, 1, Blocks.TORCH);
        return b.build();
    }

    // 1-wide bridge, 12 long
    private static Blueprint bridge() {
        var b = Blueprint.builder("bridge");
        b.box(0, 0, 0, 0, 0, 11, Blocks.OAK_PLANKS);
        b.add(0, 1, 0, Blocks.OAK_FENCE);
        b.add(0, 1, 11, Blocks.OAK_FENCE);
        return b.build();
    }

    // 10-long, 3-high wall
    private static Blueprint wall() {
        var b = Blueprint.builder("wall");
        b.box(0, 0, 0, 9, 2, 0, Blocks.COBBLESTONE);
        b.add(0, 3, 0, Blocks.TORCH);
        b.add(9, 3, 0, Blocks.TORCH);
        return b.build();
    }

    // 9x9 farm plot (farmland + water)
    private static Blueprint farm() {
        var b = Blueprint.builder("farm");
        // Water channel in middle
        for (int x = 0; x <= 8; x++) {
            b.add(x, 0, 4, Blocks.WATER);
        }
        // Farmland
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                if (z == 4) continue;
                b.add(x, 0, z, Blocks.FARMLAND);
            }
        }
        // Border logs
        b.box(0, 1, 0, 8, 1, 0, Blocks.OAK_LOG);
        b.box(0, 1, 8, 8, 1, 8, Blocks.OAK_LOG);
        return b.build();
    }

    // Small 5x5 shelter with bed
    private static Blueprint shelter() {
        var b = Blueprint.builder("shelter");
        b.box(0, 0, 0, 4, 0, 4, Blocks.DIRT);
        b.walls(0, 0, 4, 4, 1, 2, Blocks.OAK_LOG);
        b.box(0, 3, 0, 4, 3, 4, Blocks.OAK_PLANKS);
        b.add(2, 1, 0, Blocks.OAK_DOOR); // door (visual)
        b.add(1, 1, 2, Blocks.RED_BED);
        b.add(3, 1, 2, Blocks.CRAFTING_TABLE);
        b.add(2, 2, 2, Blocks.TORCH);
        return b.build();
    }

    // Staircase going up 8 blocks
    private static Blueprint stairs() {
        var b = Blueprint.builder("stairs");
        for (int i = 0; i < 8; i++) {
            b.add(0, i, i, Blocks.OAK_STAIRS);
            b.add(0, i - 1, i, Blocks.OAK_PLANKS); // support
        }
        return b.build();
    }

    // 5x5 platform
    private static Blueprint platform() {
        var b = Blueprint.builder("platform");
        b.box(0, 0, 0, 4, 0, 4, Blocks.OAK_PLANKS);
        b.add(0, 1, 0, Blocks.TORCH);
        b.add(4, 1, 0, Blocks.TORCH);
        b.add(0, 1, 4, Blocks.TORCH);
        b.add(4, 1, 4, Blocks.TORCH);
        return b.build();
    }

    // Single pillar (for getting up)
    private static Blueprint pillar() {
        var b = Blueprint.builder("pillar");
        for (int y = 0; y < 6; y++) {
            b.add(0, y, 0, Blocks.DIRT);
        }
        return b.build();
    }
}
