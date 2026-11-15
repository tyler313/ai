package com.aicompanion.ai;

import com.aicompanion.player.AIPlayer;

/**
 * A single behaviour the AI can perform (follow, mine, fight, build, ...).
 *
 * <p>The {@link Brain} scores every task each decision tick and runs the best one.
 * Tasks must be cheap to score and safe to tick every server tick.
 */
public interface Task {
    /** Human-readable name for debugging / status. */
    String name();

    /**
     * Utility score 0..100. Higher = more desirable right now.
     * Return 0 (or negative) if this task is not applicable.
     */
    double score(Brain brain);

    /** Called once when this task becomes active. */
    default void start(Brain brain) {}

    /** Called every server tick while active. */
    void tick(Brain brain);

    /** Called once when this task is replaced / finished. */
    default void stop(Brain brain) {}

    /** True when the task has completed its goal. */
    default boolean isComplete(Brain brain) { return false; }

    /** True if this task should interrupt the current one immediately (e.g. flee). */
    default boolean isUrgent(Brain brain) { return false; }
}
