package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Follow the owner at a comfortable distance. Teleports if owner is very far.
 */
public class FollowTask implements Task {
    @Override public String name() { return "Follow"; }

    @Override public double score(Brain brain) {
        if (brain.perception.owner == null) return 0;
        double d = brain.perception.ownerDistance;
        if (d < AIMod.CONFIG.followDistance) return 10; // already close
        if (d < 10) return 40;
        if (d < 32) return 70;
        return 90; // far → urgent to catch up
    }

    @Override public void tick(Brain brain) {
        ServerPlayerEntity owner = brain.perception.owner;
        if (owner == null) return;
        double d = brain.ai.player.distanceTo(owner);

        // Teleport if owner is way too far (different dimension or very distant)
        if (d > AIMod.CONFIG.teleportDistance || owner.getServerWorld() != brain.ai.player.getServerWorld()) {
            brain.movement.teleportTo(owner.getBlockPos());
            brain.say("Whoa, you got far — teleported to you!");
            return;
        }

        if (d > AIMod.CONFIG.followDistance + 1) {
            double speed = d > 16 ? 1.4 : 1.0;
            brain.movement.followEntity(owner, speed, AIMod.CONFIG.followDistance);
        } else {
            brain.movement.stop();
            brain.movement.lookAt(owner.getPos());
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return brain.perception.owner == null;
    }
}
