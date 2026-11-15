package com.aicompanion.ai;

import com.aicompanion.AIMod;
import com.aicompanion.ai.llm.LLMPlanner;
import com.aicompanion.ai.tasks.*;
import com.aicompanion.player.AIPlayer;
import com.aicompanion.util.ChatUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * The AI brain — utility AI + task scheduler.
 *
 * <p>Every {@code brainTickInterval} ticks it:
 * <ol>
 *   <li>Scans perception (entities, resources, dangers).</li>
 *   <li>Scores all registered tasks and picks the best.</li>
 *   <li>Asks the LLM planner (if enabled) for a high-level plan.</li>
 *   <li>Ticks the active task every server tick.</li>
 * </ol>
 *
 * <p>Tasks can also be forced via {@link #setForcedTask(Task)} from commands/chat.
 */
public class Brain {
    public final AIPlayer ai;
    public final Memory memory;
    public final Perception perception;
    public final Pathfinder pathfinder;
    public final MovementController movement;
    public final LLMPlanner llmPlanner;

    private final List<Task> tasks = new ArrayList<>();
    private Task currentTask = null;
    private Task forcedTask = null;
    private long lastDecisionTime = 0;
    private long lastChatTime = 0;
    private String lastChatMessage = "";
    private int tickCounter = 0;

    // Task instances (reused)
    public final FollowTask followTask;
    public final MineTask mineTask;
    public final ChopTask chopTask;
    public final BuildTask buildTask;
    public final CombatTask combatTask;
    public final FarmTask farmTask;
    public final ExploreTask exploreTask;
    public final CollectTask collectTask;
    public final CraftTask craftTask;
    public final EatTask eatTask;
    public final SleepTask sleepTask;
    public final FleeTask fleeTask;
    public final GuardTask guardTask;
    public final IdleTask idleTask;
    public final ReturnHomeTask returnHomeTask;

    public Brain(AIPlayer ai) {
        this.ai = ai;
        this.memory = new Memory(ai);
        this.perception = new Perception(ai, memory);
        this.pathfinder = new Pathfinder(ai);
        this.movement = new MovementController(ai, pathfinder);
        this.llmPlanner = new LLMPlanner(this);

        followTask = new FollowTask();
        mineTask = new MineTask();
        chopTask = new ChopTask();
        buildTask = new BuildTask();
        combatTask = new CombatTask();
        farmTask = new FarmTask();
        exploreTask = new ExploreTask();
        collectTask = new CollectTask();
        craftTask = new CraftTask();
        eatTask = new EatTask();
        sleepTask = new SleepTask();
        fleeTask = new FleeTask();
        guardTask = new GuardTask();
        idleTask = new IdleTask();
        returnHomeTask = new ReturnHomeTask();

        tasks.addAll(List.of(
                fleeTask, eatTask, sleepTask, combatTask,
                followTask, collectTask, mineTask, chopTask,
                buildTask, farmTask, craftTask, guardTask,
                exploreTask, returnHomeTask, idleTask
        ));

        if (memory.home == null) {
            memory.home = ai.pos().toImmutable();
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        tickCounter++;
        ServerPlayerEntity p = ai.player;
        if (!p.isAlive()) return;

        perception.scan();

        // Decide periodically (or immediately if forced/urgent)
        boolean shouldDecide = tickCounter % Math.max(1, AIMod.CONFIG.brainTickInterval) == 0;
        if (forcedTask != null) shouldDecide = true;

        if (shouldDecide) {
            decide();
        }

        // Tick active task
        if (currentTask != null) {
            try {
                currentTask.tick(this);
                if (currentTask.isComplete(this)) {
                    currentTask.stop(this);
                    currentTask = null;
                    forcedTask = null;
                }
            } catch (Exception e) {
                AIMod.LOGGER.error("[AI Companion] Task {} failed", currentTask.name(), e);
                currentTask = null;
                forcedTask = null;
            }
        }

        movement.tick();

        // Ambient chat / status
        maybeChatter();
    }

    private void decide() {
        // 1. Forced task (from command) always wins until complete
        if (forcedTask != null) {
            if (forcedTask.score(this) > 0 || forcedTask.isComplete(this) == false) {
                switchTo(forcedTask);
                return;
            } else {
                forcedTask = null;
            }
        }

        // 2. Urgent tasks interrupt immediately
        for (Task t : tasks) {
            if (t.isUrgent(this) && t.score(this) > 0) {
                switchTo(t);
                return;
            }
        }

        // 3. LLM planner (if enabled) — async, non-blocking; applies plan if confident
        if (AIMod.CONFIG.llmEnabled && llmPlanner.isAvailable()) {
            Task llmTask = llmPlanner.plan();
            if (llmTask != null && llmTask.score(this) > 20) {
                // Only override if LLM task is reasonably good
                switchTo(llmTask);
                return;
            }
        }

        // 4. Utility AI — score all, pick best with hysteresis
        Task best = null;
        double bestScore = 0;
        for (Task t : tasks) {
            double s;
            try {
                s = t.score(this);
            } catch (Exception e) {
                continue;
            }
            // Hysteresis: current task gets a small bonus to avoid thrashing
            if (t == currentTask) s += 5;
            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        if (best != null && best != currentTask) {
            switchTo(best);
        }
    }

    private void switchTo(Task next) {
        if (next == currentTask) return;
        if (currentTask != null) {
            try { currentTask.stop(this); } catch (Exception ignored) {}
        }
        currentTask = next;
        try { next.start(this); } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Task start failed: {}", next.name(), e);
        }
    }

    // ------------------------------------------------------------- public API

    /** Force a task (from commands / chat / LLM). */
    public void setForcedTask(Task task) {
        this.forcedTask = task;
        switchTo(task);
    }

    public void clearForcedTask() {
        this.forcedTask = null;
    }

    public Task getCurrentTask() {
        return currentTask;
    }

    public String statusString() {
        String task = currentTask != null ? currentTask.name() : "none";
        return "Task: " + task + " | " + perception.summary();
    }

    /** Say something in chat (to owner + nearby players). */
    public void say(String message) {
        if (!AIMod.CONFIG.aiChatEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastChatTime < 1500) return; // rate limit
        lastChatTime = now;
        lastChatMessage = message;
        ChatUtil.sayAsAI(ai, message);
    }

    /** Say something, bypassing rate limit (for direct replies). */
    public void sayNow(String message) {
        lastChatTime = System.currentTimeMillis();
        ChatUtil.sayAsAI(ai, message);
    }

    public void saveMemory() {
        memory.save();
    }

    // --------------------------------------------------------------- chatter

    private void maybeChatter() {
        // Occasional ambient lines — only if LLM personality enabled and idle-ish
        if (!AIMod.CONFIG.llmChatPersonality) return;
        if (currentTask != idleTask && currentTask != followTask) return;
        long now = System.currentTimeMillis();
        if (now - lastChatTime < 30000) return; // at most every 30s
        if (Math.random() > 0.02) return; // ~2% per tick while idle → rare

        String[] lines = {
                "Nice day for adventuring!",
                "I wonder what's over that hill...",
                "Following you!",
                "I can see a few resources nearby.",
                "Let me know if you need anything.",
                "This area looks safe.",
                "I should eat something soon.",
                "Ready when you are!",
        };
        say(lines[(int) (Math.random() * lines.length)]);
    }
}
