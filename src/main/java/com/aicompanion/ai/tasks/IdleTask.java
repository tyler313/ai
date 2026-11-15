package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;

/**
 * Do nothing / wander slightly. Fallback when nothing else applies.
 */
public class IdleTask implements Task {
    private long nextWander = 0;

    @Override public String name() { return "Idle"; }

    @Override public double score(Brain brain) {
        return 5; // always available as fallback
    }

    @Override public void tick(Brain brain) {
        brain.movement.stop();
        long now = System.currentTimeMillis();
        if (now > nextWander) {
            nextWander = now + 8000 + (long) (Math.random() * 8000);
            // Small random wander near home
            var home = brain.memory.home;
            if (home != null) {
                int dx = (int) (Math.random() * 10 - 5);
                int dz = (int) (Math.random() * 10 - 5);
                brain.movement.setDestination(home.add(dx, 0, dz), 0.6);
            }
        }
    }
}
