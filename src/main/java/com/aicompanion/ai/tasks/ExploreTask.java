package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Wander and discover new areas. Avoids danger, stays within a radius of home.
 */
public class ExploreTask implements Task {
    private BlockPos exploreTarget = null;
    private long nextPick = 0;
    private int explored = 0;

    @Override public String name() { return "Explore"; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.allowExploring) return 0;
        if (brain.perception.dangerLevel >= 2) return 0;
        // Explore when idle and no owner nearby to follow
        if (brain.perception.owner == null || brain.perception.ownerDistance > 32) return 25;
        return 10;
    }

    @Override public void start(Brain brain) {
        explored = 0;
        brain.say("Exploring the area!");
    }

    @Override public void tick(Brain brain) {
        long now = System.currentTimeMillis();
        if (exploreTarget == null || now > nextPick) {
            nextPick = now + 10000;
            BlockPos home = brain.memory.home != null ? brain.memory.home : brain.ai.pos();
            // Pick a point 20-60 blocks away in a random direction
            double angle = Math.random() * Math.PI * 2;
            int dist = 20 + (int) (Math.random() * 40);
            exploreTarget = home.add((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
            // Clamp Y to surface-ish (pathfinder will adjust)
            exploreTarget = new BlockPos(exploreTarget.getX(), home.getY(), exploreTarget.getZ());
            brain.movement.setDestination(exploreTarget, 1.1);
            explored++;
        }
        // If danger appears, stop exploring (Flee/Combat will take over)
        if (brain.perception.dangerLevel >= 2) {
            brain.movement.stop();
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return explored >= 6; // explore a few points then stop
    }

    @Override public void stop(Brain brain) {
        brain.say("Explored " + explored + " areas.");
    }
}
