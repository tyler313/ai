package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Return to home base.
 */
public class ReturnHomeTask implements Task {
    @Override public String name() { return "Return home"; }

    @Override public double score(Brain brain) {
        // Auto-return if very far from home and no owner to follow
        if (brain.memory.home == null) return 0;
        if (brain.perception.owner != null) return 0; // follow owner instead
        double d = brain.ai.pos().getSquaredDistance(brain.memory.home);
        if (d > 100 * 100) return 30;
        return 0;
    }

    @Override public void tick(Brain brain) {
        BlockPos home = brain.memory.home;
        if (home == null) return;
        double d = brain.ai.pos().getSquaredDistance(home);
        if (d > 16) {
            brain.movement.setDestination(home, 1.2);
        } else {
            brain.movement.stop();
        }
    }

    @Override public boolean isComplete(Brain brain) {
        if (brain.memory.home == null) return true;
        return brain.ai.pos().getSquaredDistance(brain.memory.home) < 16;
    }
}
