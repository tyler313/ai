package com.aicompanion.chat;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.build.Blueprints;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based natural-language parser for chat commands.
 * Understands things like:
 * <ul>
 *   <li>"mine 10 iron" / "get me some diamonds" / "mine diamond ore"</li>
 *   <li>"follow me" / "come here" / "stay"</li>
 *   <li>"build a house" / "build a tower here"</li>
 *   <li>"attack that zombie" / "kill the skeleton" / "hunt cows"</li>
 *   <li>"chop wood" / "get wood" / "farm wheat"</li>
 *   <li>"craft torches" / "make sticks"</li>
 *   <li>"explore" / "guard this place" / "go home" / "eat" / "sleep"</li>
 * </ul>
 * If an LLM is configured, it handles anything this parser doesn't.
 */
public class NLPParser {

    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    /** Parse {@code text} into a Task, or null if not understood. */
    public static Task parse(Brain brain, String text) {
        if (text == null || text.isBlank()) return null;
        String t = " " + text.toLowerCase().trim() + " ";

        // --- Follow / stay ---
        if (containsAny(t, "follow me", "follow", "come here", "come to me", "stay with me", "stick with me")) {
            return brain.followTask;
        }
        if (containsAny(t, "stay", "stop", "wait here", "don't move", "do not move", "halt")) {
            brain.guardTask.guardAt(brain.ai.pos(), 0);
            return brain.guardTask;
        }

        // --- Mine ---
        if (containsAny(t, "mine", "dig", "get me", "fetch", "find", "collect", "gather")) {
            String block = detectBlock(t);
            int count = detectCount(t, 8);
            if (block != null) {
                return brain.mineTask.configure(block, count);
            }
            // Generic "get me wood" → chop
            if (containsAny(t, "wood", "log", "logs", "tree", "trees", "planks")) {
                return brain.chopTask.configure(count);
            }
        }

        // --- Chop ---
        if (containsAny(t, "chop", "cut down", "lumberjack")) {
            return brain.chopTask.configure(detectCount(t, 16));
        }

        // --- Build ---
        if (containsAny(t, "build", "make a", "construct", "create a")) {
            String structure = detectStructure(t);
            if (structure != null) {
                BlockPos anchor = null;
                if (brain.perception.owner != null) {
                    anchor = brain.perception.owner.getBlockPos().add(4, 0, 0);
                }
                return brain.buildTask.configure(structure, anchor);
            }
        }

        // --- Combat ---
        if (containsAny(t, "attack", "kill", "fight", "defend", "hunt", "slay", "murder")) {
            LivingEntity target = detectEntity(brain, t);
            if (target != null) {
                return brain.combatTask.attack(target);
            }
            if (containsAny(t, "cow", "cows", "pig", "pigs", "sheep", "chicken", "chickens", "animal", "animals", "food")) {
                return brain.combatTask.huntAnimals(true);
            }
            return brain.combatTask;
        }

        // --- Farm ---
        if (containsAny(t, "farm", "harvest", "plant", "crop", "crops", "wheat", "carrot", "potato", "beetroot")) {
            String crop = detectCrop(t);
            return brain.farmTask.configure(crop, detectCount(t, 32));
        }

        // --- Craft ---
        if (containsAny(t, "craft", "make", "create")) {
            String item = detectCraftable(t);
            if (item != null) {
                return brain.craftTask.configure(item, detectCount(t, 16));
            }
        }

        // --- Explore / guard / home ---
        if (containsAny(t, "explore", "wander", "roam", "discover", "scout")) {
            return brain.exploreTask;
        }
        if (containsAny(t, "guard", "protect", "patrol", "watch this", "defend this")) {
            BlockPos center = brain.perception.owner != null
                    ? brain.perception.owner.getBlockPos() : brain.memory.home;
            brain.guardTask.guardAt(center, 12);
            return brain.guardTask;
        }
        if (containsAny(t, "go home", "return home", "head home", "base")) {
            return brain.returnHomeTask;
        }

        // --- Eat / sleep ---
        if (containsAny(t, "eat", "hungry", "food", "snack")) {
            return brain.eatTask;
        }
        if (containsAny(t, "sleep", "bed", "night", "rest")) {
            return brain.sleepTask;
        }

        // --- Collect ---
        if (containsAny(t, "pick up", "collect", "grab", "loot")) {
            return brain.collectTask;
        }

        return null;
    }

    // ------------------------------------------------------------ detectors

    private static boolean containsAny(String text, String... phrases) {
        for (String p : phrases) {
            if (text.contains(p)) return true;
        }
        return false;
    }

    private static int detectCount(String text, int def) {
        Matcher m = NUMBER.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        // Word numbers
        if (text.contains("a few") || text.contains("some")) return 8;
        if (text.contains("lots") || text.contains("many") || text.contains("stack")) return 32;
        if (text.contains("one") || text.contains("a ")) return 1;
        return def;
    }

    private static String detectBlock(String t) {
        if (t.contains("diamond")) return "diamond_ore";
        if (t.contains("iron")) return "iron_ore";
        if (t.contains("gold")) return "gold_ore";
        if (t.contains("coal")) return "coal_ore";
        if (t.contains("redstone")) return "redstone_ore";
        if (t.contains("lapis")) return "lapis_lazuli_ore";
        if (t.contains("emerald")) return "emerald_ore";
        if (t.contains("ancient debris") || t.contains("netherite")) return "ancient_debris";
        if (t.contains("stone")) return "stone";
        if (t.contains("cobble")) return "cobblestone";
        if (t.contains("dirt")) return "dirt";
        if (t.contains("sand")) return "sand";
        if (t.contains("gravel")) return "gravel";
        if (t.contains("obsidian")) return "obsidian";
        if (t.contains("quartz")) return "nether_quartz_ore";
        return null;
    }

    private static String detectStructure(String t) {
        for (String name : Blueprints.names()) {
            if (t.contains(name)) return name;
        }
        if (t.contains("home") || t.contains("house") || t.contains("shelter") || t.contains("hut")) return "house";
        if (t.contains("tower") || t.contains("castle")) return "tower";
        if (t.contains("bridge")) return "bridge";
        if (t.contains("wall") || t.contains("fence")) return "wall";
        if (t.contains("farm")) return "farm";
        if (t.contains("stair")) return "stairs";
        if (t.contains("platform")) return "platform";
        if (t.contains("pillar") || t.contains("column")) return "pillar";
        return null;
    }

    private static LivingEntity detectEntity(Brain brain, String t) {
        // Named mob types
        String[] mobNames = {"zombie", "skeleton", "creeper", "spider", "enderman", "witch", "slime", "phantom", "drowned", "husk", "stray", "pillager", "vindicator", "evoker", "blaze", "ghast", "piglin", "cow", "pig", "sheep", "chicken", "horse", "wolf", "cat", "bee"};
        for (String mob : mobNames) {
            if (t.contains(mob)) {
                // Find nearest entity of that type
                for (LivingEntity e : brain.perception.nearbyHostiles) {
                    if (e.getName().getString().toLowerCase().contains(mob)) return e;
                }
                for (LivingEntity e : brain.perception.nearbyAnimals) {
                    if (e.getName().getString().toLowerCase().contains(mob)) return e;
                }
                // Fall back to nearest hostile/animal
                if (brain.perception.nearestHostile() != null) return brain.perception.nearestHostile();
                return brain.perception.nearestAnimal();
            }
        }
        // "that" / "this" / "him" / "her" → nearest hostile
        if (t.contains("that") || t.contains("this") || t.contains("him") || t.contains("her") || t.contains("it")) {
            if (brain.perception.nearestHostile() != null) return brain.perception.nearestHostile();
            return brain.perception.nearestAnimal();
        }
        return null;
    }

    private static String detectCrop(String t) {
        if (t.contains("carrot")) return "carrots";
        if (t.contains("potato")) return "potatoes";
        if (t.contains("beet")) return "beetroots";
        return "wheat";
    }

    private static String detectCraftable(String t) {
        if (t.contains("torch")) return "torch";
        if (t.contains("stick")) return "sticks";
        if (t.contains("plank")) return "oak_planks";
        if (t.contains("crafting table") || t.contains("workbench")) return "crafting_table";
        if (t.contains("pickaxe") || t.contains("pick")) return t.contains("stone") ? "stone_pickaxe" : "wooden_pickaxe";
        if (t.contains("axe")) return t.contains("stone") ? "stone_axe" : "wooden_axe";
        if (t.contains("sword")) return t.contains("stone") ? "stone_sword" : "wooden_sword";
        return null;
    }

    /** Is this text addressed to the AI? (starts with AI name or "ai") */
    public static boolean isAddressedToAI(String aiName, String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.startsWith(aiName.toLowerCase()) || t.startsWith("ai ") || t.startsWith("ai,")
                || t.startsWith("hey ai") || t.startsWith("yo ai");
    }
}
