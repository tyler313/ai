package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.BlockUtil;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Farm: till soil, plant seeds, harvest mature crops, replant.
 */
public class FarmTask implements Task {
    public String crop = "wheat"; // wheat, carrots, potatoes, beetroots
    public int targetHarvest = 32;
    public int harvested = 0;

    private BlockPos current = null;
    private long lastAction = 0;

    @Override public String name() { return "Farm " + crop; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.allowFarming) return 0;
        if (brain.perception.dangerLevel >= 2) return 0;
        // Auto-farm if mature crops nearby
        if (brain.perception.hasResource("crop")) return 45;
        return 0;
    }

    @Override public void start(Brain brain) {
        harvested = 0;
        current = null;
        brain.say("Farming " + crop + ".");
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();

        if (current == null || world.getBlockState(current).isAir()) {
            current = findWork(brain);
            if (current == null) {
                brain.say("No farm work nearby.");
                return;
            }
        }

        double dist = p.getBlockPos().getSquaredDistance(current);
        if (dist > 16) {
            BlockPos stand = brain.pathfinder.findStandable(world, current);
            if (stand != null) brain.movement.setDestination(stand, 1.1);
            brain.movement.lookAt(current);
            return;
        }

        brain.movement.stop();
        brain.movement.lookAt(current);

        long now = System.currentTimeMillis();
        if (now - lastAction < 300) return;
        lastAction = now;

        BlockState state = world.getBlockState(current);
        var block = state.getBlock();

        // Mature crop → harvest
        if (block instanceof CropBlock cropBlock && cropBlock.isMature(state)) {
            try {
                world.breakBlock(current, true, p);
                harvested++;
                brain.memory.blocksMined++;
                current = null;
                return;
            } catch (Exception ignored) {}
        }

        // Farmland without crop → plant
        if (state.isOf(Blocks.FARMLAND)) {
            int seedSlot = findSeedSlot(brain);
            if (seedSlot >= 0) {
                try {
                    var seedBlock = seedBlockFor(crop);
                    world.setBlockState(current.up(), seedBlock.getDefaultState(), 3);
                    p.getInventory().getStack(seedSlot).decrement(1);
                    current = null;
                    return;
                } catch (Exception ignored) {}
            }
        }

        // Dirt/grass → till with hoe
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT_PATH)) {
            int hoeSlot = InventoryUtil.findItemSlot(p, Items.DIAMOND_HOE);
            if (hoeSlot < 0) hoeSlot = InventoryUtil.findItemSlot(p, Items.IRON_HOE);
            if (hoeSlot < 0) hoeSlot = InventoryUtil.findItemSlot(p, Items.STONE_HOE);
            if (hoeSlot < 0) hoeSlot = InventoryUtil.findItemSlot(p, Items.WOODEN_HOE);
            if (hoeSlot >= 0) {
                try {
                    world.setBlockState(current, Blocks.FARMLAND.getDefaultState(), 3);
                    current = null;
                    return;
                } catch (Exception ignored) {}
            }
        }

        // Nothing to do here — move on
        current = null;
    }

    private BlockPos findWork(Brain brain) {
        // Prefer mature crops, then farmland, then dirt
        var crops = new ArrayList<>(brain.perception.getResources("crop"));
        if (!crops.isEmpty()) {
            crops.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(brain.ai.pos())));
            return crops.get(0).toImmutable();
        }
        // Scan for farmland / dirt near AI
        ServerWorld world = brain.ai.player.getServerWorld();
        BlockPos center = brain.ai.pos();
        for (int r = 2; r <= 16; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    BlockPos pos = center.add(dx, 0, dz);
                    BlockState s = world.getBlockState(pos);
                    if (s.isOf(Blocks.FARMLAND) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRASS_BLOCK)) {
                        return pos.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private int findSeedSlot(Brain brain) {
        var p = brain.ai.player;
        return switch (crop) {
            case "wheat" -> InventoryUtil.findItemSlot(p, Items.WHEAT_SEEDS);
            case "carrots", "carrot" -> InventoryUtil.findItemSlot(p, Items.CARROT);
            case "potatoes", "potato" -> InventoryUtil.findItemSlot(p, Items.POTATO);
            case "beetroots", "beetroot" -> InventoryUtil.findItemSlot(p, Items.BEETROOT_SEEDS);
            default -> InventoryUtil.findItemSlot(p, Items.WHEAT_SEEDS);
        };
    }

    private net.minecraft.block.Block seedBlockFor(String crop) {
        return switch (crop) {
            case "wheat" -> Blocks.WHEAT;
            case "carrots", "carrot" -> Blocks.CARROTS;
            case "potatoes", "potato" -> Blocks.POTATOES;
            case "beetroots", "beetroot" -> Blocks.BEETROOTS;
            default -> Blocks.WHEAT;
        };
    }

    @Override public boolean isComplete(Brain brain) {
        return harvested >= targetHarvest;
    }

    @Override public void stop(Brain brain) {
        brain.say("Harvested " + harvested + " " + crop + ".");
        brain.memory.save();
    }

    public FarmTask configure(String crop, int count) {
        this.crop = crop;
        this.targetHarvest = Math.max(1, count);
        return this;
    }
}
