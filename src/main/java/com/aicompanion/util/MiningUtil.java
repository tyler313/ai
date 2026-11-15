package com.aicompanion.util;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Timed block breaking for the fake-player AI.
 *
 * <p>Fake players have no client sending block-break actions, so instead of
 * driving the interaction manager we track our own break progress: hold the
 * "pickaxe" on the block for as long as vanilla would need, then break it and
 * roll the drops as if the player mined it.
 */
public final class MiningUtil {

    private static final int MIN_MILLIS = 120;
    private static final int MAX_MILLIS = 30_000;

    private record Progress(int playerId, long started) {
    }

    private static final Map<BlockPos, Progress> progress = new HashMap<>();

    private MiningUtil() {
    }

    /**
     * Continue breaking {@code pos}. Call once per tick while the AI is
     * standing next to and looking at the block.
     *
     * @return true once the block has been broken (or was already gone)
     */
    public static boolean tickBreak(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            progress.remove(pos);
            return true;
        }

        float hardness = state.getHardness(world, pos);
        if (hardness < 0) {
            // Unbreakable (bedrock, barrier, ...)
            progress.remove(pos);
            return false;
        }

        long now = System.currentTimeMillis();
        long required = requiredMillis(player, state, hardness);

        Progress p = progress.get(pos);
        if (p == null || p.playerId() != player.getId()) {
            progress.put(pos, new Progress(player.getId(), now));
            p = progress.get(pos);
        }

        // Swing the arm so other players see the mining animation.
        player.swingHand(Hand.MAIN_HAND);

        if (now - p.started() < required) {
            return false;
        }

        boolean broken = world.breakBlock(pos, true, player);
        progress.remove(pos);
        prune(now);
        return broken || world.getBlockState(pos).isAir();
    }

    /** True if the block is currently being broken by the given player. */
    public static boolean isBreaking(ServerPlayerEntity player, BlockPos pos) {
        Progress p = progress.get(pos);
        return p != null && p.playerId() == player.getId();
    }

    public static void reset(BlockPos pos) {
        progress.remove(pos);
    }

    public static void clear() {
        progress.clear();
    }

    private static long requiredMillis(ServerPlayerEntity player, BlockState state, float hardness) {
        if (hardness <= 0.0f) return MIN_MILLIS;
        float speed = player.getInventory().getBlockBreakingSpeed(state);
        if (speed <= 0.0f) speed = 1.0f;
        // Vanilla: a harvestable block takes hardness*30/speed ticks.
        double ticks = (hardness * 30.0) / speed;
        long ms = Math.round(ticks * 50.0);
        return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, ms));
    }

    /** Drop stale progress entries (e.g. the AI walked away). */
    private static void prune(long now) {
        if (progress.size() < 64) return;
        Iterator<Map.Entry<BlockPos, Progress>> it = progress.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().started() > 60_000L) {
                it.remove();
            }
        }
    }
}
