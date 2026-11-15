package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Run away from lava, fire, or overwhelming enemies when health is critical.
 */
public class FleeTask implements Task {
    private BlockPos fleeTarget = null;
    private long fleeUntil = 0;

    @Override public String name() { return "Flee"; }

    @Override public double score(Brain brain) {
        var p = brain.ai.player;
        double hpFrac = p.getHealth() / p.getMaxHealth();
        if (hpFrac < 0.15f && brain.perception.dangerLevel >= 2) return 100;
        if (hpFrac < 0.25f && brain.perception.dangerLevel >= 3) return 95;
        // Standing in/near lava or fire
        if (brain.perception.nearestLava != null
                && p.getBlockPos().getSquaredDistance(brain.perception.nearestLava) < 4) return 100;
        if (brain.perception.nearestFire != null
                && p.getBlockPos().getSquaredDistance(brain.perception.nearestFire) < 4) return 100;
        if (p.isOnFire()) return 100;
        return 0;
    }

    @Override public boolean isUrgent(Brain brain) {
        return score(brain) >= 95;
    }

    @Override public void start(Brain brain) {
        fleeUntil = System.currentTimeMillis() + 5000;
        brain.say("Too dangerous! Retreating!");
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        // Pick a flee direction away from nearest threat
        LivingEntity threat = brain.perception.nearestHostile();
        BlockPos away;
        if (threat != null) {
            double dx = p.getX() - threat.getX();
            double dz = p.getZ() - threat.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.1) { dx = 1; dz = 0; len = 1; }
            away = p.getBlockPos().add((int) (dx / len * 12), 0, (int) (dz / len * 12));
        } else if (brain.perception.nearestLava != null) {
            BlockPos lava = brain.perception.nearestLava;
            double dx = p.getX() - lava.getX();
            double dz = p.getZ() - lava.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.1) { dx = 1; dz = 0; len = 1; }
            away = p.getBlockPos().add((int) (dx / len * 10), 0, (int) (dz / len * 10));
        } else {
            away = p.getBlockPos().add(8, 0, 8);
        }
        brain.movement.setDestination(away, 1.5);
        fleeUntil = System.currentTimeMillis() + 2000; // keep fleeing while danger persists
    }

    @Override public boolean isComplete(Brain brain) {
        return System.currentTimeMillis() > fleeUntil && brain.perception.dangerLevel < 2;
    }
}
