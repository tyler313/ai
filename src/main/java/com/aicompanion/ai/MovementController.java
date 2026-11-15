package com.aicompanion.ai;

import com.aicompanion.AIMod;
import com.aicompanion.player.AIPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Follows a path computed by {@link Pathfinder} using vanilla navigation.
 * Handles stuck detection, jumping, and teleport-if-far recovery.
 */
public class MovementController {
    private final AIPlayer ai;
    private final Pathfinder pathfinder;

    private List<BlockPos> path = List.of();
    private int pathIndex = 0;
    private BlockPos destination = null;
    private double speed = 1.0;
    private long lastRepath = 0;
    private BlockPos lastPos = null;
    private int stuckCounter = 0;
    private long lastProgressTime = 0;

    public MovementController(AIPlayer ai, Pathfinder pathfinder) {
        this.ai = ai;
        this.pathfinder = pathfinder;
        this.lastProgressTime = System.currentTimeMillis();
    }

    /** Set a new destination. Recomputes path immediately. */
    public void setDestination(BlockPos dest, double speed) {
        if (dest == null) {
            stop();
            return;
        }
        if (destination != null && destination.equals(dest) && !path.isEmpty()) {
            this.speed = speed;
            return; // already heading there
        }
        this.destination = dest.toImmutable();
        this.speed = speed;
        repath();
    }

    /** Move toward a specific entity (repaths periodically). */
    public void followEntity(net.minecraft.entity.Entity target, double speed, double stopDistance) {
        if (target == null) return;
        ServerPlayerEntity p = ai.player;
        double dist = p.distanceTo(target);
        if (dist <= stopDistance) {
            p.getNavigation().stop();
            lookAt(target.getPos());
            return;
        }
        // Repath every 2s or if destination moved significantly
        BlockPos targetPos = target.getBlockPos();
        if (destination == null || !destination.equals(targetPos) || System.currentTimeMillis() - lastRepath > 2000) {
            setDestination(targetPos, speed);
        }
    }

    public void tick() {
        ServerPlayerEntity p = ai.player;
        if (destination == null) return;

        // Arrived?
        BlockPos cur = p.getBlockPos();
        if (cur.getSquaredDistance(destination) < 4) {
            p.getNavigation().stop();
            path = List.of();
            pathIndex = 0;
            destination = null;
            return;
        }

        // Need a path?
        if (path.isEmpty() || pathIndex >= path.size()) {
            repath();
            if (path.isEmpty()) {
                // No path — try vanilla direct navigation as fallback
                p.getNavigation().startMovingTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, speed);
                return;
            }
        }

        // Follow waypoints
        BlockPos waypoint = path.get(pathIndex);
        double dx = waypoint.getX() + 0.5 - p.getX();
        double dy = waypoint.getY() - p.getY();
        double dz = waypoint.getZ() + 0.5 - p.getZ();
        double distToWaypoint = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distToWaypoint < 1.2) {
            pathIndex++;
            return;
        }

        // Steer toward waypoint
        if (!p.getNavigation().isFollowingPath()) {
            p.getNavigation().startMovingTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, speed);
        }

        // Face movement direction
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.setYaw(yaw);
        p.setPitch((float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))));

        // Jump if blocked ahead and waypoint is higher
        if (waypoint.getY() > cur.getY()) {
            p.jump();
        }

        checkStuck(cur);
    }

    private void repath() {
        ServerWorld world = ai.player.getServerWorld();
        path = pathfinder.findPath(world, ai.player.getBlockPos(), destination);
        pathIndex = 0;
        lastRepath = System.currentTimeMillis();
        stuckCounter = 0;
        lastPos = ai.player.getBlockPos().toImmutable();
        lastProgressTime = System.currentTimeMillis();
    }

    private void checkStuck(BlockPos cur) {
        long now = System.currentTimeMillis();
        if (lastPos == null) {
            lastPos = cur.toImmutable();
            return;
        }
        // Made progress?
        if (cur.getSquaredDistance(lastPos) > 1) {
            lastPos = cur.toImmutable();
            lastProgressTime = now;
            stuckCounter = 0;
            return;
        }
        // No progress for 3 seconds → stuck
        if (now - lastProgressTime > 3000) {
            stuckCounter++;
            lastProgressTime = now;
            lastPos = cur.toImmutable();

            ServerPlayerEntity p = ai.player;
            if (stuckCounter == 1) {
                // Try jumping + repath
                p.jump();
                repath();
            } else if (stuckCounter == 2) {
                // Try breaking the block in front (pillar / dig out)
                BlockPos ahead = cur.add(
                        (int) Math.round(Math.sin(Math.toRadians(p.getYaw())) * -1),
                        0,
                        (int) Math.round(Math.cos(Math.toRadians(p.getYaw())) * -1));
                ServerWorld world = p.getServerWorld();
                var state = world.getBlockState(ahead);
                if (!state.isAir() && state.getHardness(world, ahead) >= 0) {
                    world.breakBlock(ahead, true);
                }
                repath();
            } else if (stuckCounter >= 3) {
                // Give up and teleport to destination if it's the owner, else clear
                if (destination != null && ai.brain != null && ai.brain.perception.owner != null) {
                    BlockPos ownerPos = ai.brain.perception.owner.getBlockPos();
                    if (destination.getSquaredDistance(ownerPos) < 9) {
                        teleportTo(destination);
                        stop();
                        return;
                    }
                }
                // Otherwise just stop trying for now
                stop();
            }
        }
    }

    public void teleportTo(BlockPos pos) {
        ServerPlayerEntity p = ai.player;
        BlockPos safe = pathfinder.findStandable(p.getServerWorld(), pos);
        if (safe == null) safe = pos;
        p.setPos(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        p.setVelocity(Vec3d.ZERO);
        p.getNavigation().stop();
    }

    public void stop() {
        destination = null;
        path = List.of();
        pathIndex = 0;
        try {
            ai.player.getNavigation().stop();
        } catch (Exception ignored) {}
    }

    public boolean isMoving() {
        return destination != null;
    }

    public BlockPos getDestination() {
        return destination;
    }

    public void lookAt(Vec3d target) {
        ServerPlayerEntity p = ai.player;
        double dx = target.x - p.getX();
        double dy = target.y - p.getEyePos().y;
        double dz = target.z - p.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        p.setYaw(yaw);
        p.setPitch(pitch);
    }

    public void lookAt(BlockPos pos) {
        lookAt(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }
}
