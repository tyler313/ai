package com.aicompanion.ai;

import com.aicompanion.AIMod;
import com.aicompanion.player.AIPlayer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * A* pathfinder over the voxel grid.
 *
 * <p>Handles walking, jumping (1–2 blocks), swimming, climbing, and
 * avoids lava/fire/cactus when configured. Returns a smoothed waypoint list.
 * Falls back to vanilla navigation if A* fails (e.g. very long distance).
 */
public class Pathfinder {
    private final AIPlayer ai;

    private static final int MAX_NODES = 8000;
    private static final int MAX_DISTANCE = 128;

    // 6 cardinal + 4 diagonal horizontal neighbours (no flying)
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 0}, {0, -1, 0},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
    };

    public Pathfinder(AIPlayer ai) {
        this.ai = ai;
    }

    /**
     * Find a path from the AI's current position to {@code target}.
     * Returns an empty list if no path found.
     */
    public List<BlockPos> findPath(BlockPos target) {
        ServerWorld world = ai.player.getServerWorld();
        BlockPos start = ai.player.getBlockPos();
        return findPath(world, start, target);
    }

    public List<BlockPos> findPath(ServerWorld world, BlockPos start, BlockPos target) {
        if (start.equals(target)) return List.of();
        if (start.getSquaredDistance(target) > MAX_DISTANCE * MAX_DISTANCE) {
            // Too far for A* — return direct target so vanilla nav can try
            return List.of(target.toImmutable());
        }

        // Adjust target to a standable position if needed
        BlockPos goal = findStandable(world, target);
        if (goal == null) return List.of();

        Map<BlockPos, Node> open = new HashMap<>();
        Map<BlockPos, Node> closed = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        Node startNode = new Node(start.toImmutable(), null, 0, heuristic(start, goal));
        open.put(start, startNode);
        queue.add(startNode);

        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_NODES) {
            Node current = queue.poll();
            if (current == null) break;
            if (closed.containsKey(current.pos)) continue;
            closed.put(current.pos, current);
            visited++;

            if (current.pos.equals(goal)) {
                return reconstruct(current);
            }

            for (int[] off : OFFSETS) {
                BlockPos next = current.pos.add(off[0], off[1], off[2]);
                if (closed.containsKey(next)) continue;
                if (!isWalkable(world, next, current.pos)) continue;

                double stepCost = cost(world, next, current.pos);
                if (stepCost < 0) continue; // blocked / dangerous

                double g = current.g + stepCost;
                Node existing = open.get(next);
                if (existing == null || g < existing.g) {
                    Node n = new Node(next.toImmutable(), current, g, g + heuristic(next, goal));
                    open.put(next, n);
                    queue.add(n);
                }
            }
        }

        // A* failed — try greedy fallback: step toward target if walkable
        return greedyFallback(world, start, goal);
    }

    private List<BlockPos> reconstruct(Node node) {
        LinkedList<BlockPos> path = new LinkedList<>();
        Node n = node;
        while (n != null) {
            path.addFirst(n.pos);
            n = n.parent;
        }
        // Remove start position
        if (!path.isEmpty()) path.removeFirst();
        return smooth(path);
    }

    /**
     * Remove redundant waypoints where a straight line is walkable.
     */
    private List<BlockPos> smooth(List<BlockPos> path) {
        if (path.size() <= 2) return path;
        ServerWorld world = ai.player.getServerWorld();
        List<BlockPos> out = new ArrayList<>();
        int i = 0;
        out.add(path.get(0));
        while (i < path.size() - 1) {
            int furthest = i + 1;
            for (int j = path.size() - 1; j > i + 1; j--) {
                if (lineWalkable(world, path.get(i), path.get(j))) {
                    furthest = j;
                    break;
                }
            }
            out.add(path.get(furthest));
            i = furthest;
        }
        return out;
    }

    private boolean lineWalkable(ServerWorld world, BlockPos a, BlockPos b) {
        // Sample along the line; all intermediate feet positions must be walkable
        double dist = Math.sqrt(a.getSquaredDistance(b));
        int steps = (int) Math.ceil(dist);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            BlockPos p = new BlockPos(
                    (int) Math.round(a.getX() + (b.getX() - a.getX()) * t),
                    (int) Math.round(a.getY() + (b.getY() - a.getY()) * t),
                    (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
            if (!isWalkable(world, p, a)) return false;
        }
        return true;
    }

    private List<BlockPos> greedyFallback(ServerWorld world, BlockPos start, BlockPos goal) {
        // Simple: if goal is walkable, return it directly (vanilla nav will handle)
        if (isWalkable(world, goal, start)) {
            return List.of(goal.toImmutable());
        }
        // Otherwise try nearby standable positions
        BlockPos alt = findStandable(world, goal);
        if (alt != null && isWalkable(world, alt, start)) {
            return List.of(alt.toImmutable());
        }
        return List.of();
    }

    // ------------------------------------------------------------ walkability

    /**
     * Can the AI stand at {@code pos} (feet position)?
     * Requires solid ground below (or water/ladder), and 2 air blocks at feet/head.
     */
    public boolean isWalkable(ServerWorld world, BlockPos pos, BlockPos from) {
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());
        BlockState ground = world.getBlockState(pos.down());

        // Feet and head must be passable (air, water, tall grass, etc.)
        if (!isPassable(feet) || !isPassable(head)) return false;

        // Ground must be solid, or water (swim), or ladder/vine (climb)
        boolean groundSolid = ground.isSolidBlock(world, pos.down());
        boolean inWater = feet.isOf(Blocks.WATER) || head.isOf(Blocks.WATER);
        boolean climbing = feet.isOf(Blocks.LADDER) || feet.isOf(Blocks.VINE)
                || feet.isOf(Blocks.CAVE_VINES) || feet.isOf(Blocks.TWISTING_VINES)
                || feet.isOf(Blocks.WEEPING_VINES);

        if (!groundSolid && !inWater && !climbing) {
            // Allow falling one block: check if there's ground 1-2 below
            BlockState g2 = world.getBlockState(pos.down(2));
            BlockState g3 = world.getBlockState(pos.down(3));
            if (!g2.isSolidBlock(world, pos.down(2)) && !g3.isSolidBlock(world, pos.down(3))) {
                return false;
            }
        }

        // Danger check
        if (AIMod.CONFIG.avoidLava) {
            if (feet.isOf(Blocks.LAVA) || head.isOf(Blocks.LAVA) || ground.isOf(Blocks.LAVA)) return false;
            if (feet.isOf(Blocks.FIRE) || feet.isOf(Blocks.SOUL_FIRE)) return false;
            if (feet.isOf(Blocks.CACTUS) || head.isOf(Blocks.CACTUS)) return false;
            if (feet.isOf(Blocks.MAGMA_BLOCK)) return false;
        }

        // Don't walk into walls when moving horizontally (prevents corner cutting)
        if (from != null && pos.getY() == from.getY()) {
            int dx = pos.getX() - from.getX();
            int dz = pos.getZ() - from.getZ();
            if (dx != 0 && dz != 0) {
                // Diagonal: both orthogonal neighbours must be passable
                BlockPos side1 = from.add(dx, 0, 0);
                BlockPos side2 = from.add(0, 0, dz);
                if (!isPassable(world.getBlockState(side1)) || !isPassable(world.getBlockState(side1.up()))) return false;
                if (!isPassable(world.getBlockState(side2)) || !isPassable(world.getBlockState(side2.up()))) return false;
            }
        }

        return true;
    }

    private boolean isPassable(BlockState state) {
        if (state.isAir()) return true;
        var block = state.getBlock();
        return block == Blocks.WATER || block == Blocks.TALL_GRASS || block == Blocks.SHORT_GRASS
                || block == Blocks.SNOW || block == Blocks.TORCH || block == Blocks.WALL_TORCH
                || block == Blocks.LADDER || block == Blocks.VINE || block == Blocks.SCAFFOLDING
                || block == Blocks.COBWEB || block == Blocks.SUGAR_CANE || block == Blocks.DEAD_BUSH;
    }

    private double cost(ServerWorld world, BlockPos pos, BlockPos from) {
        double base = Math.sqrt(pos.getSquaredDistance(from));
        // Jump penalty
        if (pos.getY() > from.getY()) base += 1.5;
        // Falling is cheap
        if (pos.getY() < from.getY()) base += 0.3;
        // Water is slow
        BlockState feet = world.getBlockState(pos);
        if (feet.isOf(Blocks.WATER)) base += 2.0;
        // Prefer paths (avoid trampling crops)
        if (feet.isOf(Blocks.WHEAT) || feet.isOf(Blocks.CARROTS) || feet.isOf(Blocks.POTATOES)) base += 3.0;
        return base;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        // Octile-ish heuristic
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Find the nearest standable position to {@code target} (searches up/down/around).
     */
    public BlockPos findStandable(ServerWorld world, BlockPos target) {
        if (isWalkable(world, target, null)) return target.toImmutable();
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos up = target.up(dy);
            if (isWalkable(world, up, null)) return up.toImmutable();
        }
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos down = target.down(dy);
            if (isWalkable(world, down, null)) return down.toImmutable();
        }
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = target.add(dx, 0, dz);
                    if (isWalkable(world, p, null)) return p.toImmutable();
                    BlockPos pu = p.up();
                    if (isWalkable(world, pu, null)) return pu.toImmutable();
                }
            }
        }
        return null;
    }

    private static class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }
}
