package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Patrol around home / owner and attack hostiles that come close.
 */
public class GuardTask implements Task {
    public BlockPos guardCenter = null;
    public int patrolRadius = 12;

    private long nextPatrol = 0;
    private BlockPos patrolTarget = null;

    @Override public String name() { return "Guard"; }

    @Override public double score(Brain brain) {
        // Only auto-guard if explicitly set (forced task), otherwise low score
        return guardCenter != null ? 30 : 0;
    }

    @Override public void start(Brain brain) {
        if (guardCenter == null) {
            guardCenter = brain.perception.owner != null
                    ? brain.perception.owner.getBlockPos().toImmutable()
                    : brain.memory.home;
        }
        brain.say("Guarding this area. I'll patrol and defend.");
    }

    @Override public void tick(Brain brain) {
        // If hostile nearby, combat task will take over via utility scoring;
        // here we just patrol.
        long now = System.currentTimeMillis();
        if (now > nextPatrol || patrolTarget == null) {
            nextPatrol = now + 6000 + (long) (Math.random() * 4000);
            BlockPos center = guardCenter != null ? guardCenter : brain.memory.home;
            if (center != null) {
                double angle = Math.random() * Math.PI * 2;
                int r = (int) (Math.random() * patrolRadius);
                patrolTarget = center.add((int) (Math.cos(angle) * r), 0, (int) (Math.sin(angle) * r));
                brain.movement.setDestination(patrolTarget, 0.9);
            }
        }
        // Look around while patrolling
        brain.ai.player.setYaw(brain.ai.player.getYaw() + 0.5f);
    }

    @Override public boolean isComplete(Brain brain) {
        return false; // guard until cancelled
    }

    public GuardTask guardAt(BlockPos pos, int radius) {
        this.guardCenter = pos != null ? pos.toImmutable() : null;
        this.patrolRadius = radius;
        return this;
    }
}
