package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.BlockUtil;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Chop trees for logs. Replants saplings if configured.
 */
public class ChopTask implements Task {
    public int targetCount = 16;
    public int choppedCount = 0;

    private BlockPos currentLog = null;
    private long lastHit = 0;

    @Override public String name() { return "Chop trees"; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.allowMining) return 0;
        if (brain.perception.dangerLevel >= 3) return 0;
        // Auto-chop if low on logs/planks and logs are nearby
        int logs = InventoryUtil.countItem(brain.ai.player, net.minecraft.item.Items.OAK_LOG)
                + InventoryUtil.countItem(brain.ai.player, net.minecraft.item.Items.SPRUCE_LOG)
                + InventoryUtil.countItem(brain.ai.player, net.minecraft.item.Items.BIRCH_LOG);
        if (logs < 8 && brain.perception.hasResource("log")) return 50;
        return 0;
    }

    @Override public void start(Brain brain) {
        choppedCount = 0;
        currentLog = null;
        brain.say("Chopping wood — getting " + targetCount + " logs.");
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();

        if (InventoryUtil.isFull(p)) {
            BlockPos deposit = brain.memory.home;
            if (deposit != null) brain.movement.setDestination(deposit, 1.2);
            return;
        }

        if (currentLog == null || world.getBlockState(currentLog).isAir()) {
            currentLog = findNearestLog(brain);
            if (currentLog == null) {
                brain.say("No trees nearby.");
                return;
            }
        }

        double dist = p.getBlockPos().getSquaredDistance(currentLog);
        if (dist > 16) {
            BlockPos stand = brain.pathfinder.findStandable(world, currentLog);
            if (stand != null) brain.movement.setDestination(stand, 1.2);
            brain.movement.lookAt(currentLog);
        } else {
            brain.movement.stop();
            brain.movement.lookAt(currentLog);
            long now = System.currentTimeMillis();
            if (now - lastHit > 250) {
                lastHit = now;
                InventoryUtil.equipBestTool(p, world.getBlockState(currentLog));
                try {
                    float hardness = world.getBlockState(currentLog).getHardness(world, currentLog);
                    if (hardness >= 0 && com.aicompanion.util.MiningUtil.tickBreak(p, currentLog)) {
                        if (world.getBlockState(currentLog).isAir()) {
                            choppedCount++;
                            brain.memory.blocksMined++;
                            // Replant sapling at base if configured
                            if (AIMod.CONFIG.replantTrees) {
                                replant(brain, currentLog);
                            }
                            currentLog = null;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void replant(Brain brain, BlockPos logPos) {
        // Find ground below and place a sapling if we have one
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();
        BlockPos ground = logPos.down();
        var groundState = world.getBlockState(ground);
        if (!groundState.isOf(Blocks.GRASS_BLOCK) && !groundState.isOf(Blocks.DIRT)
                && !groundState.isOf(Blocks.PODZOL) && !groundState.isOf(Blocks.COARSE_DIRT)) {
            return;
        }
        int saplingSlot = InventoryUtil.findItemSlot(p, net.minecraft.item.Items.OAK_SAPLING);
        if (saplingSlot < 0) saplingSlot = InventoryUtil.findItemSlot(p, net.minecraft.item.Items.SPRUCE_SAPLING);
        if (saplingSlot < 0) return;
        try {
            var stack = p.getInventory().getStack(saplingSlot);
            world.setBlockState(logPos, Blocks.OAK_SAPLING.getDefaultState(), 3);
            stack.decrement(1);
        } catch (Exception ignored) {}
    }

    private BlockPos findNearestLog(Brain brain) {
        var logs = brain.perception.getResources("log");
        if (!logs.isEmpty()) {
            logs.sort(java.util.Comparator.comparingDouble(pos -> pos.getSquaredDistance(brain.ai.pos())));
            return logs.get(0).toImmutable();
        }
        // Wider scan for any log
        return BlockUtil.findNearestBlock(brain.ai.player.getServerWorld(), brain.ai.pos(),
                net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of("minecraft", "oak_log")), 48);
    }

    @Override public boolean isComplete(Brain brain) {
        return choppedCount >= targetCount;
    }

    @Override public void stop(Brain brain) {
        brain.say("Chopped " + choppedCount + " logs.");
        brain.memory.save();
    }

    public ChopTask configure(int count) {
        this.targetCount = Math.max(1, count);
        return this;
    }
}
