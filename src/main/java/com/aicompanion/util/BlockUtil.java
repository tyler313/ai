package com.aicompanion.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Block helpers: registry lookup, nearest-block search, lava checks.
 */
public class BlockUtil {

    /** Look up a block by registry path ("diamond_ore", "oak_log", ...). */
    public static Block fromRegistryPath(String path) {
        if (path == null) return null;
        String p = path.toLowerCase().trim();
        if (p.startsWith("minecraft:")) p = p.substring("minecraft:".length());
        // Try exact, then with _ore / _log suffixes
        Block b = Registries.BLOCK.get(Identifier.of("minecraft", p));
        if (b != null && b != Blocks.AIR) return b;
        // Common aliases
        return switch (p) {
            case "diamond", "diamonds" -> Blocks.DIAMOND_ORE;
            case "iron", "iron_ore" -> Blocks.IRON_ORE;
            case "gold", "gold_ore" -> Blocks.GOLD_ORE;
            case "coal", "coal_ore" -> Blocks.COAL_ORE;
            case "redstone" -> Blocks.REDSTONE_ORE;
            case "lapis", "lapis_lazuli" -> Blocks.LAPIS_ORE;
            case "emerald" -> Blocks.EMERALD_ORE;
            case "stone" -> Blocks.STONE;
            case "cobble", "cobblestone" -> Blocks.COBBLESTONE;
            case "dirt" -> Blocks.DIRT;
            case "wood", "log", "logs", "oak_log" -> Blocks.OAK_LOG;
            case "planks", "oak_planks" -> Blocks.OAK_PLANKS;
            case "sand" -> Blocks.SAND;
            case "gravel" -> Blocks.GRAVEL;
            case "netherite", "ancient_debris" -> Blocks.ANCIENT_DEBRIS;
            default -> Blocks.AIR;
        };
    }

    /** Find the nearest block of {@code type} within {@code radius} of {@code center}. */
    public static BlockPos findNearestBlock(ServerWorld world, BlockPos center, Block block, int radius) {
        if (block == null || block == Blocks.AIR) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -16; dy <= 16; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (world.getBlockState(pos).isOf(block)) {
                        double d = pos.getSquaredDistance(center);
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos.toImmutable();
                            if (d < 4) return best; // close enough
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Is any of the 6 neighbours lava? (lava-pocket safety check) */
    public static boolean isAdjacentToLava(ServerWorld world, BlockPos pos) {
        for (var dir : net.minecraft.util.math.Direction.values()) {
            BlockPos n = pos.offset(dir);
            if (world.getBlockState(n).isOf(Blocks.LAVA)) return true;
        }
        return false;
    }

    /** Is this block safe to stand on / replace? */
    public static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.isOf(Blocks.WATER) || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.SNOW);
    }
}
