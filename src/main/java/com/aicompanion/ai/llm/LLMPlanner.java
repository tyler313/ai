package com.aicompanion.ai.llm;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.chat.NLPParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.CompletableFuture;

/**
 * Uses an LLM to pick high-level plans. Falls back to {@link NLPParser} rules.
 *
 * <p>The LLM receives a JSON snapshot of perception + inventory and returns
 * a JSON plan like {@code {"task":"mine","block":"diamond_ore","count":8}}.
 * The plan is mapped to a concrete {@link Task}.
 */
public class LLMPlanner {
    private final Brain brain;
    private final LLMClient client = new LLMClient();
    private CompletableFuture<String> pending = null;
    private long lastPlanTime = 0;
    private static final long PLAN_COOLDOWN_MS = 15000;

    public LLMPlanner(Brain brain) {
        this.brain = brain;
    }

    public boolean isAvailable() {
        return client.isConfigured();
    }

    /**
     * Ask the LLM for a plan. Non-blocking: returns the last completed plan,
     * or null if none ready. Kicks off a new request if cooldown expired.
     */
    public Task plan() {
        long now = System.currentTimeMillis();
        // Return completed plan if available
        if (pending != null && pending.isDone()) {
            String result = pending.join();
            pending = null;
            if (result != null) {
                Task t = parsePlan(result);
                if (t != null) return t;
            }
        }
        // Kick off new request if idle and cooldown passed
        if (pending == null && now - lastPlanTime > PLAN_COOLDOWN_MS && isAvailable()) {
            lastPlanTime = now;
            String system = systemPrompt();
            String user = userPrompt();
            pending = client.chatAsync(system, user);
        }
        return null;
    }

    private String systemPrompt() {
        return """
                You are Aria, a Minecraft AI companion. You play alongside a human player.
                You can: follow, mine blocks, chop trees, build structures, fight mobs, farm, craft, eat, sleep, explore, guard, collect items, return home, flee danger.
                Reply with ONLY a JSON object, no other text. Example:
                {"task":"mine","block":"diamond_ore","count":8}
                {"task":"follow"}
                {"task":"build","structure":"house"}
                {"task":"combat"}
                {"task":"farm","crop":"wheat","count":32}
                {"task":"craft","item":"torch","count":16}
                {"task":"explore"}
                {"task":"guard"}
                {"task":"eat"}
                {"task":"sleep"}
                {"task":"flee"}
                {"task":"idle"}
                Choose the most useful task given the world state. If in danger, flee or fight. If hungry, eat. If night and bed nearby, sleep.
                """;
    }

    private String userPrompt() {
        var p = brain.ai.player;
        StringBuilder sb = new StringBuilder();
        sb.append("World state: ").append(brain.perception.summary()).append("\n");
        sb.append("Inventory: ").append(inventorySummary()).append("\n");
        sb.append("Time: ").append(brain.perception.isNight ? "night" : "day").append("\n");
        sb.append("Owner distance: ").append(brain.perception.ownerDistance).append("\n");
        sb.append("Home: ").append(brain.memory.home).append("\n");
        sb.append("Health: ").append(p.getHealth()).append("/").append(p.getMaxHealth()).append("\n");
        sb.append("Hunger: ").append(p.getHungerManager().getFoodLevel()).append("/20\n");
        return sb.toString();
    }

    private String inventorySummary() {
        var p = brain.ai.player;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.getInventory().size(); i++) {
            var s = p.getInventory().getStack(i);
            if (!s.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getCount()).append("x ").append(s.getItem().toString());
            }
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    private Task parsePlan(String json) {
        try {
            // Extract JSON object (LLM might wrap in markdown)
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end < 0) return null;
            JsonObject o = JsonParser.parseString(json.substring(start, end + 1)).getAsJsonObject();
            String task = o.has("task") ? o.get("task").getAsString().toLowerCase() : "idle";
            return switch (task) {
                case "mine" -> brain.mineTask.configure(
                        o.has("block") ? o.get("block").getAsString() : "diamond_ore",
                        o.has("count") ? o.get("count").getAsInt() : 8);
                case "chop", "wood" -> brain.chopTask.configure(o.has("count") ? o.get("count").getAsInt() : 16);
                case "build" -> brain.buildTask.configure(
                        o.has("structure") ? o.get("structure").getAsString() : "house", null);
                case "combat", "fight", "attack" -> brain.combatTask;
                case "farm" -> brain.farmTask.configure(
                        o.has("crop") ? o.get("crop").getAsString() : "wheat",
                        o.has("count") ? o.get("count").getAsInt() : 32);
                case "craft" -> brain.craftTask.configure(
                        o.has("item") ? o.get("item").getAsString() : "torch",
                        o.has("count") ? o.get("count").getAsInt() : 16);
                case "follow" -> brain.followTask;
                case "explore" -> brain.exploreTask;
                case "guard" -> brain.guardTask;
                case "eat" -> brain.eatTask;
                case "sleep" -> brain.sleepTask;
                case "flee" -> brain.fleeTask;
                case "collect" -> brain.collectTask;
                case "home", "return" -> brain.returnHomeTask;
                default -> brain.idleTask;
            };
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Failed to parse LLM plan: {}", e.getMessage());
            return null;
        }
    }

    /** Generate a chatty reply via LLM (for /ai say). */
    public String chatReply(String playerMessage) {
        if (!isAvailable()) return null;
        String system = "You are Aria, a friendly Minecraft AI companion. Reply briefly (1-2 sentences), in character. "
                + "You follow the player, mine, build, fight, and farm with them. "
                + "World: " + brain.perception.summary();
        return client.chat(system, playerMessage);
    }
}
