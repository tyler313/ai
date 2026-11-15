package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.entity.Entity;

/**
 * Pick up dropped items nearby.
 */
public class CollectTask implements Task {
    @Override public String name() { return "Collect"; }

    @Override public double score(Brain brain) {
        if (brain.perception.nearbyDrops.isEmpty()) return 0;
        Entity drop = brain.perception.nearestDrop();
        if (drop == null) return 0;
        double d = brain.ai.player.distanceTo(drop);
        if (d < 2) return 60; // walk over it
        if (d < 16) return 35;
        return 0;
    }

    @Override public void tick(Brain brain) {
        Entity drop = brain.perception.nearestDrop();
        if (drop == null) return;
        double d = brain.ai.player.distanceTo(drop);
        if (d > 1.5) {
            brain.movement.setDestination(drop.getBlockPos(), 1.3);
        } else {
            brain.movement.stop();
            brain.memory.itemsCollected++;
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return brain.perception.nearbyDrops.isEmpty();
    }
}
