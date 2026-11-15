package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Sleep at night in a bed if one is known/nearby.
 */
public class SleepTask implements Task {
    @Override public String name() { return "Sleep"; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.autoSleep) return 0;
        if (!brain.perception.isNight) return 0;
        if (brain.perception.dangerLevel >= 2) return 0; // don't sleep in danger
        BlockPos bed = brain.memory.bedPos != null ? brain.memory.bedPos : brain.perception.nearestBed;
        if (bed == null) return 0;
        return 45;
    }

    @Override public void tick(Brain brain) {
        BlockPos bed = brain.memory.bedPos != null ? brain.memory.bedPos : brain.perception.nearestBed;
        if (bed == null) return;
        brain.memory.bedPos = bed.toImmutable();

        var p = brain.ai.player;
        if (p.getBlockPos().getSquaredDistance(bed) > 9) {
            brain.movement.setDestination(bed, 1.0);
        } else {
            brain.movement.stop();
            brain.movement.lookAt(bed);
            // Try to sleep via interaction
            try {
                p.getInteractionManager().interactBlock(p, net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.util.hit.BlockHitResult(
                        net.minecraft.util.math.Vec3d.ofCenter(bed), net.minecraft.util.math.Direction.UP, bed, false));
            } catch (Exception ignored) {}
            // Lie down visually by setting pose via sleeping flag if possible
            try {
                p.setSleepingPos(bed);
            } catch (Exception ignored) {}
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return !brain.perception.isNight;
    }
}
