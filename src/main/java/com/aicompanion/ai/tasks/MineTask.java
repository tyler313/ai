package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.BlockUtil;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Mine a specific block type (ores, stone, dirt, ...).
 * Finds the nearest target, paths to it, mines with the best tool,
 * collects drops, and repeats until the requested count is reached.
 */
public class MineTask implements Task {
    public String targetBlock = "diamond_ore"; // registry path, e.g. "diamond_ore"
    public int targetCount = 8;
    public int minedCount = 0;

    private BlockPos currentTarget = null;
    private long lastMineAttempt = 0;
    private int stuckOnTarget = 0;
    private BlockPos lastTargetPos = null;

    @Override public String name() { return "Mine " + targetBlock; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.allowMining) return 0;
        if (brain.perception.dangerLevel >= 3) return 0;
        // Only auto-score if resources are nearby (forced tasks score via setForcedTask)
        if (brain.perception.hasResource(targetBlock) || brain.perception.hasResource(shortName(targetBlock))) {
            return 55;
        }
        return 0;
    }

    @Override public void start(Brain brain) {
        minedCount = 0;
        currentTarget = null;
        brain.say("On it — mining " + targetCount + "x " + targetBlock + ".");
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();

        // Inventory full? Go deposit at home/chest
        if (InventoryUtil.isFull(p)) {
            BlockPos deposit = brain.memory.chestPos != null ? brain.memory.chestPos : brain.memory.home;
            if (deposit != null) {
                brain.movement.setDestination(deposit, 1.2);
                brain.say("Inventory full — heading home to drop off.");
            }
            return;
        }

        // Find target if needed
        if (currentTarget == null || world.getBlockState(currentTarget).isAir()) {
            currentTarget = findNearestTarget(brain);
            if (currentTarget == null) {
                brain.say("I can't find any " + targetBlock + " nearby.");
                return;
            }
            lastTargetPos = currentTarget.toImmutable();
            stuckOnTarget = 0;
        }

        // Stuck on same target? Skip it
        if (currentTarget.equals(lastTargetPos)) {
            stuckOnTarget++;
            if (stuckOnTarget > 200) {
                // Mark as mined-out by moving on
                currentTarget = null;
                return;
            }
        } else {
            lastTargetPos = currentTarget.toImmutable();
            stuckOnTarget = 0;
        }

        double dist = p.getBlockPos().getSquaredDistance(currentTarget);
        if (dist > 16) {
            // Path to adjacent standable position
            BlockPos stand = brain.pathfinder.findStandable(world, currentTarget);
            if (stand == null) {
                currentTarget = null;
                return;
            }
            brain.movement.setDestination(stand, 1.2);
            brain.movement.lookAt(currentTarget);
        } else {
            brain.movement.stop();
            brain.movement.lookAt(currentTarget);
            mineBlock(brain, currentTarget);
        }
    }

    private void mineBlock(Brain brain, BlockPos pos) {
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            currentTarget = null;
            return;
        }
        // Never mine bedrock
        if (state.isOf(Blocks.BEDROCK)) {
            currentTarget = null;
            return;
        }
        // Don't mine lava-adjacent blocks blindly (lava pocket risk)
        if (AIMod.CONFIG.avoidLava && BlockUtil.isAdjacentToLava(world, pos)) {
            brain.say("Careful — lava nearby! Skipping that block.");
            currentTarget = null;
            return;
        }

        // Equip best tool
        InventoryUtil.equipBestTool(p, state);

        try {
            // Face the block and keep swinging until it breaks.
            brain.movement.lookAt(pos);
            if (com.aicompanion.util.MiningUtil.tickBreak(p, pos)) {
                minedCount++;
                brain.memory.blocksMined++;
                currentTarget = null;
            }
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Mine failed", e);
        }
    }

    private BlockPos findNearestTarget(Brain brain) {
        // Check perception first, then memory, then scan wider
        List<BlockPos> candidates = new ArrayList<>();
        candidates.addAll(brain.perception.getResources(targetBlock));
        candidates.addAll(brain.perception.getResources(shortName(targetBlock)));
        // Also check by block instance
        Block block = BlockUtil.fromRegistryPath(targetBlock);
        if (block != null) {
            candidates.addAll(brain.perception.getResources(com.aicompanion.ai.Perception.blockName(block)));
        }
        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(brain.ai.pos())));
            return candidates.get(0).toImmutable();
        }
        // Wider scan around AI
        return BlockUtil.findNearestBlock(brain.ai.player.getServerWorld(), brain.ai.pos(), block, 48);
    }

    private String shortName(String full) {
        // "diamond_ore" -> "diamond" for matching
        if (full.endsWith("_ore")) return full.substring(0, full.length() - 4);
        return full;
    }

    @Override public boolean isComplete(Brain brain) {
        return minedCount >= targetCount;
    }

    @Override public void stop(Brain brain) {
        brain.say("Mined " + minedCount + " " + targetBlock + ".");
        brain.memory.save();
    }

    /** Configure this task (used by commands/chat). */
    public MineTask configure(String block, int count) {
        this.targetBlock = block;
        this.targetCount = Math.max(1, count);
        return this;
    }
}
