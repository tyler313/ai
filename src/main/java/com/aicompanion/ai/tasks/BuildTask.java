package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.build.Blueprint;
import com.aicompanion.build.Blueprints;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Build structures from blueprints (house, tower, bridge, wall, farm, shelter...).
 * Places blocks one by one, managing inventory and scaffolding.
 */
public class BuildTask implements Task {
    public String structure = "house";
    public BlockPos anchor = null; // where to build (defaults to near owner/home)

    private Blueprint blueprint = null;
    private int index = 0;
    private long lastPlace = 0;
    private int placed = 0;
    private int skipped = 0;

    @Override public String name() { return "Build " + structure; }

    @Override public double score(Brain brain) {
        // Only runs when forced via command/chat
        return 0;
    }

    @Override public void start(Brain brain) {
        blueprint = Blueprints.get(structure);
        if (blueprint == null) {
            brain.say("I don't know how to build '" + structure + "'. Try: house, tower, bridge, wall, farm, shelter, stairs, platform.");
            return;
        }
        if (anchor == null) {
            if (brain.perception.owner != null) {
                anchor = brain.perception.owner.getBlockPos().add(4, 0, 0);
            } else if (brain.memory.home != null) {
                anchor = brain.memory.home.add(4, 0, 0);
            } else {
                anchor = brain.ai.pos().add(4, 0, 0);
            }
        }
        // Flatten ground at anchor (simple)
        flatten(brain, anchor, blueprint.sizeX(), blueprint.sizeZ());
        index = 0;
        placed = 0;
        skipped = 0;
        brain.say("Building a " + structure + " at " + anchor + " — " + blueprint.blocks().size() + " blocks.");
    }

    private void flatten(Brain brain, BlockPos anchor, int sx, int sz) {
        ServerWorld world = brain.ai.player.getServerWorld();
        int groundY = anchor.getY();
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                BlockPos p = anchor.add(x, 0, z);
                // Find surface height
                BlockPos surface = p;
                while (surface.getY() > groundY - 4 && !world.getBlockState(surface.down()).isSolidBlock(world, surface.down())) {
                    surface = surface.down();
                }
                // Fill holes below ground level
                for (int y = surface.getY() + 1; y < groundY; y++) {
                    BlockPos fill = new BlockPos(p.getX(), y, p.getZ());
                    if (world.getBlockState(fill).isAir()) {
                        world.setBlockState(fill, net.minecraft.block.Blocks.DIRT.getDefaultState(), 3);
                    }
                }
            }
        }
    }

    @Override public void tick(Brain brain) {
        if (blueprint == null || anchor == null) return;
        var p = brain.ai.player;
        ServerWorld world = p.getServerWorld();

        if (index >= blueprint.blocks().size()) return;

        Blueprint.PlacedBlock entry = blueprint.blocks().get(index);
        BlockPos target = anchor.add(entry.offset());
        Block block = entry.block();

        // Already correct? Skip
        BlockState existing = world.getBlockState(target);
        if (existing.isOf(block)) {
            index++;
            return;
        }

        // Need the block in inventory?
        int slot = InventoryUtil.findBlockSlot(p, block);
        if (slot < 0) {
            // Try to craft/fetch — for now, report missing and skip
            skipped++;
            index++;
            if (skipped % 10 == 0) {
                brain.say("Missing " + block + " — skipping some blocks. Bring more materials!");
            }
            return;
        }

        // Move close to target
        double dist = p.getBlockPos().getSquaredDistance(target);
        if (dist > 25) {
            BlockPos stand = brain.pathfinder.findStandable(world, target);
            if (stand != null) brain.movement.setDestination(stand, 1.2);
            brain.movement.lookAt(target);
            return;
        }

        brain.movement.stop();
        brain.movement.lookAt(target);

        long now = System.currentTimeMillis();
        if (now - lastPlace < 200) return;
        lastPlace = now;

        // Place block
        try {
            ItemStack stack = p.getInventory().getStack(slot);
            // Equip to selected slot
            int prevSlot = p.getInventory().selectedSlot;
            p.getInventory().selectedSlot = slot;
            // Place via world set (reliable for fake players)
            world.setBlockState(target, block.getDefaultState(), 3);
            stack.decrement(1);
            p.getInventory().selectedSlot = prevSlot;
            placed++;
            brain.memory.blocksPlaced++;
            index++;
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Build place failed", e);
            index++;
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return blueprint == null || index >= blueprint.blocks().size();
    }

    @Override public void stop(Brain brain) {
        if (blueprint != null) {
            brain.say("Built " + structure + ": " + placed + " placed, " + skipped + " skipped.");
            brain.memory.save();
        }
        blueprint = null;
    }

    public BuildTask configure(String structure, BlockPos anchor) {
        this.structure = structure;
        this.anchor = anchor;
        return this;
    }
}
