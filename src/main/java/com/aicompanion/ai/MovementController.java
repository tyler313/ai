package com.aicompanion.ai;

import com.aicompanion.player.AIPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Moves the AI along a path produced by {@link Pathfinder}.
 *
 * <p>A fake player has no client pushing movement packets, so instead of mob
 * navigation we step the player's position directly along the smoothed path.
 * Position, yaw and pitch are synced to real clients automatically, so the
 * companion looks like a player walking around. Stuck recovery: jump/step →
 * break the blocking block → replan.
 */
public class MovementController {
    /** Blocks moved per tick at speed 1.0 (~4.3 blocks/second). */
    private static final double BASE_STEP = 0.215;

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

    // ------------------------------------------------------------------ api

    /** Set a new destination. Recomputes the path immediately. */
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

    /** Walk toward an entity (repaths periodically). */
    public void followEntity(net.minecraft.entity.Entity target, double speed, double stopDistance) {
        if (target == null) return;
        ServerPlayerEntity p = ai.player;
        if (p.distanceTo(target) <= stopDistance) {
            stop();
            lookAt(target.getPos());
            return;
        }
        BlockPos targetPos = target.getBlockPos();
        if (destination == null || !destination.equals(targetPos)
                || System.currentTimeMillis() - lastRepath > 2000) {
            setDestination(targetPos, speed);
        }
    }

    /** Advance the AI along its path. Call once per server tick. */
    public void tick() {
        ServerPlayerEntity p = ai.player;
        if (destination == null) return;

        BlockPos cur = p.getBlockPos();
        if (cur.getSquaredDistance(destination) < 4) {
            stop();
            return;
        }

        if (path.isEmpty() || pathIndex >= path.size()) {
            repath();
            if (path.isEmpty()) {
                // No path: head straight at the target and rely on stuck handling.
                stepToward(new Vec3d(destination.getX() + 0.5, p.getY(), destination.getZ() + 0.5));
                checkStuck(cur);
                return;
            }
        }

        BlockPos waypoint = path.get(pathIndex);
        Vec3d target = new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
        double horiz = Math.hypot(target.x - p.getX(), target.z - p.getZ());

        if (horiz < 0.9 && Math.abs(target.y - p.getY()) < 1.2) {
            pathIndex++;
            if (pathIndex >= path.size()) {
                stop();
            }
            return;
        }

        stepToward(target);
        checkStuck(cur);
    }

    public void stop() {
        destination = null;
        path = List.of();
        pathIndex = 0;
        stuckCounter = 0;
    }

    public boolean isMoving() {
        return destination != null;
    }

    public BlockPos getDestination() {
        return destination;
    }

    public void teleportTo(BlockPos pos) {
        ServerPlayerEntity p = ai.player;
        BlockPos safe = pathfinder.findStandable(p.getServerWorld(), pos);
        if (safe == null) safe = pos;
        p.setPos(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0.0f;
        stop();
    }

    public void lookAt(Vec3d target) {
        ServerPlayerEntity p = ai.player;
        double dx = target.x - p.getX();
        double dy = target.y - p.getEyePos().y;
        double dz = target.z - p.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        p.setYaw(yaw);
        p.setHeadYaw(yaw);
        p.setPitch(pitch);
    }

    public void lookAt(BlockPos pos) {
        lookAt(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    // ------------------------------------------------------------- stepping

    /**
     * Move one small step toward {@code target}, keeping the AI standing on
     * solid ground: flat walking, one-block step up, or a short drop.
     */
    private void stepToward(Vec3d target) {
        ServerPlayerEntity p = ai.player;
        ServerWorld world = p.getServerWorld();

        double dx = target.x - p.getX();
        double dz = target.z - p.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz < 1.0e-4) return;

        // Face the direction of travel — players look where they walk.
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.setYaw(yaw);
        p.setHeadYaw(yaw);
        p.setBodyYaw(yaw);

        double step = Math.min(horiz, BASE_STEP * Math.max(0.4, speed));
        double nx = p.getX() + (dx / horiz) * step;
        double nz = p.getZ() + (dz / horiz) * step;

        int feetY = p.getBlockPos().getY();
        int targetY = resolveStepY(world, nx, feetY, nz);
        if (targetY == Integer.MIN_VALUE) {
            // Blocked: dig out (handled by stuck recovery in checkStuck).
            return;
        }

        // Smooth vertical movement (max one block per tick).
        double ny = p.getY();
        double desiredY = targetY;
        if (desiredY > ny) {
            ny = Math.min(desiredY, ny + 0.4);
            if (ny - p.getY() > 0.05) {
                p.setPitch(-20.0f);
            }
        } else if (desiredY < ny) {
            ny = Math.max(desiredY, ny - 0.5);
        }

        p.setPos(nx, ny, nz);
        p.setVelocity(Vec3d.ZERO);
        p.setOnGround(true);
        p.fallDistance = 0.0f;

        if (p.getVelocity().horizontalLengthSquared() > 0.01) {
            p.setSprinting(true);
        }
    }

    /**
     * Find the Y level the AI can stand at when moving to {@code (x, z)}.
     * Returns {@link Integer#MIN_VALUE} when the way is blocked.
     */
    private int resolveStepY(ServerWorld world, double x, int feetY, double z) {
        for (int dy = 1; dy >= -2; dy--) {
            int y = feetY + dy;
            BlockPos feet = BlockPos.ofFloored(x, y, z);
            if (!isPassable(world, feet) || !isPassable(world, feet.up())) {
                continue;
            }
            BlockPos ground = feet.down();
            if (world.getBlockState(ground).isSolidBlock(world, ground)) {
                return y;
            }
            // Swimming / standing in water
            if (!world.getFluidState(feet).isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private boolean isPassable(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (state.isAir()) return true;
        if (!world.getFluidState(pos).isEmpty()) return true;
        return state.getCollisionShape(world, pos).isEmpty();
    }

    // --------------------------------------------------------------- stuck

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
        if (cur.getSquaredDistance(lastPos) > 1) {
            lastPos = cur.toImmutable();
            lastProgressTime = now;
            stuckCounter = 0;
            return;
        }
        // No progress for 3 seconds → stuck
        if (now - lastProgressTime <= 3000) return;

        lastProgressTime = now;
        lastPos = cur.toImmutable();
        stuckCounter++;

        ServerPlayerEntity p = ai.player;
        ServerWorld world = p.getServerWorld();

        if (stuckCounter == 1) {
            // Nudge up (helps with slabs/stairs) and replan.
            p.setPos(p.getX(), p.getY() + 0.6, p.getZ());
            repath();
        } else if (stuckCounter == 2) {
            // Dig out the block in the way.
            if (destination != null) {
                Vec3d dir = new Vec3d(destination.getX() + 0.5 - p.getX(), 0,
                        destination.getZ() + 0.5 - p.getZ());
                if (dir.horizontalLengthSquared() > 1.0e-4) {
                    Vec3d n = dir.normalize();
                    BlockPos ahead = BlockPos.ofFloored(p.getX() + n.x * 1.2, p.getY() + 0.1, p.getZ() + n.z * 1.2);
                    BlockPos above = ahead.up();
                    for (BlockPos b : new BlockPos[]{ahead, above}) {
                        var state = world.getBlockState(b);
                        if (!state.isAir() && state.getHardness(world, b) >= 0
                                && state.getHardness(world, b) < 50) {
                            world.breakBlock(b, true, p);
                        }
                    }
                }
            }
            repath();
        } else {
            // Last resort: teleport onto the path if the destination is close,
            // otherwise give up on this leg.
            if (destination != null && destination.getSquaredDistance(p.getBlockPos()) < 25) {
                BlockPos safe = pathfinder.findStandable(world, destination);
                if (safe != null) {
                    teleportTo(safe);
                }
            }
            stop();
        }
    }
}
