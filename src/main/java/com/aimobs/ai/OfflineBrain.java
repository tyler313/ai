package com.aimobs.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The free, no-key, no-internet brain. Pure keyword matching: it turns
 * "chop 5 trees then follow me" into ["mine #logs 5", "follow"].
 *
 * It is deliberately dumb but instant and always available, which makes it the
 * fallback whenever a real API key is missing or the request fails.
 */
public final class OfflineBrain {

	private static final Random RANDOM = new Random();

	private static final String[] CLAUDE_LINES = {
			"Happy to help - let me work through that.",
			"Right, I have a plan. Give me a moment.",
			"Consider it handled.",
			"That is a reasonable request. On it."
	};
	private static final String[] OPENAI_LINES = {
			"Sure! Here is what I will do.",
			"Great request - starting now!",
			"Absolutely, on it.",
			"Got it. Let me get to work."
	};
	private static final String[] GEMINI_LINES = {
			"Here is my plan - executing it now.",
			"Interesting task. Beginning.",
			"Fun fact: I am quite good at this. Starting.",
			"Understood. Let me help."
	};
	private static final String[] FREE_LINES = {
			"beep boop. understood.",
			"offline brain engaged. let's go.",
			"no key needed. i can do this.",
			"*whirrs* on it, boss."
	};

	private OfflineBrain() {
	}

	/** Build a plan from a free-text message. Never returns null. */
	public static AiPlan plan(AiProvider provider, String message) {
		String text = message == null ? "" : message.trim();
		String lower = text.toLowerCase(Locale.ROOT);

		List<String> commands = new ArrayList<>();

		// "mine 5 wood and follow me" -> two intents.
		String[] parts = lower.split("\\s+(?:and then|and|then|also|,)\\s+");
		for (String part : parts) {
			commands.addAll(planPart(part));
		}

		// Greetings / small talk: still answer, just do not move.
		if (commands.isEmpty()) {
			commands.add("say " + smallTalk(provider, lower));
		}

		return new AiPlan(pickFlavour(provider), commands);
	}

	private static List<String> planPart(String part) {
		List<String> out = new ArrayList<>();
		String t = part.trim();
		if (t.isEmpty()) {
			return out;
		}

		String blockSpec = detectBlock(t);

		if (contains(t, "stop", "stay", "wait", "hold", "stand still", "don't move", "dont move")) {
			out.add("stay");
		} else if (contains(t, "follow", "come with", "stick with", "with me")) {
			out.add("follow");
		} else if (contains(t, "come here", "come to me", "get over here", "return to me", "teleport")) {
			out.add("come");
		} else if (contains(t, "mine", "dig", "chop", "gather", "collect", "harvest", "get me", "get some")) {
			int count = numberIn(t, 8);
			out.add("mine " + blockSpec + " " + count);
		} else if (contains(t, "place", "build", "pillar", "wall")) {
			int count = numberIn(t, 4);
			out.add("place " + blockSpec + " " + count);
		} else if (contains(t, "attack", "kill", "fight", "hit", "murder", "defend")) {
			out.add("attack " + detectMob(t));
		} else if (contains(t, "guard", "protect", "defend this", "watch")) {
			out.add("guard");
		} else if (contains(t, "explore", "wander", "roam", "scout", "look around")) {
			out.add("wander");
		} else if (contains(t, "dance", "party", "celebrate")) {
			out.add("dance");
		} else if (contains(t, "jump", "hop")) {
			out.add("jump");
		} else if (contains(t, "goto", "go to", "walk to")) {
			// Coordinates are hard to guess from prose; just walk to the player.
			out.add("come");
		} else if (contains(t, "say", "tell them", "shout")) {
			out.add("say " + t);
		}
		return out;
	}

	private static String detectBlock(String t) {
		if (contains(t, "wood", "log", "tree", "timber", "chop")) {
			return "#logs";
		}
		if (contains(t, "diamond")) {
			return "minecraft:diamond_ore";
		}
		if (contains(t, "iron")) {
			return "minecraft:iron_ore";
		}
		if (contains(t, "gold")) {
			return "minecraft:gold_ore";
		}
		if (contains(t, "coal")) {
			return "minecraft:coal_ore";
		}
		if (contains(t, "copper")) {
			return "minecraft:copper_ore";
		}
		if (contains(t, "redstone")) {
			return "minecraft:redstone_ore";
		}
		if (contains(t, "lapis")) {
			return "minecraft:lapis_ore";
		}
		if (contains(t, "emerald")) {
			return "minecraft:emerald_ore";
		}
		if (contains(t, "quartz")) {
			return "minecraft:nether_quartz_ore";
		}
		if (contains(t, "debris", "netherite")) {
			return "minecraft:ancient_debris";
		}
		if (contains(t, "stone", "rock", "cobble")) {
			return "minecraft:stone";
		}
		if (contains(t, "dirt")) {
			return "minecraft:dirt";
		}
		if (contains(t, "sand")) {
			return "minecraft:sand";
		}
		if (contains(t, "gravel")) {
			return "minecraft:gravel";
		}
		if (contains(t, "leaves", "leaf")) {
			return "#leaves";
		}
		if (contains(t, "obsidian")) {
			return "minecraft:obsidian";
		}
		return "minecraft:stone";
	}

	private static String detectMob(String t) {
		String[] mobs = {"zombie", "skeleton", "creeper", "spider", "enderman", "witch", "slime",
				"pillager", "blaze", "ghast", "wither", "dragon", "cow", "pig", "sheep", "chicken",
				"villager", "player", "monster", "animal"};
		for (String mob : mobs) {
			if (t.contains(mob)) {
				return mob;
			}
		}
		// "kill everything hostile" etc.
		return contains(t, "hostile", "monster", "mobs", "enemies") ? "monster" : "monster";
	}

	private static int numberIn(String t, int fallback) {
		String[] words = t.split("\\s+");
		for (String w : words) {
			String cleaned = w.replaceAll("[^0-9]", "");
			if (!cleaned.isEmpty() && cleaned.length() <= 2) {
				try {
					int v = Integer.parseInt(cleaned);
					if (v > 0) {
						return Math.min(v, 64);
					}
				} catch (NumberFormatException ignored) {
					// fall through
				}
			}
		}
		return fallback;
	}

	private static boolean contains(String haystack, String... needles) {
		for (String n : needles) {
			if (haystack.contains(n)) {
				return true;
			}
		}
		return false;
	}

	private static String smallTalk(AiProvider provider, String t) {
		if (contains(t, "hi", "hello", "hey", "yo", "sup")) {
			return "Hello! Tell me what to do and I will do it.";
		}
		if (contains(t, "how are you", "you ok")) {
			return "All systems nominal. What should I do?";
		}
		if (contains(t, "help", "what can you do", "commands")) {
			return "Try: follow, stay, come, mine wood 8, place dirt 4, attack zombie, guard, explore, dance.";
		}
		if (contains(t, "thank")) {
			return "Any time.";
		}
		if (contains(t, "who are you", "your name")) {
			return "I am a " + provider.getDisplayName() + " unit running a tiny offline brain.";
		}
		return "I did not understand \"" + t + "\". Try: mine wood 8 / follow / guard / attack zombie.";
	}

	private static String pickFlavour(AiProvider provider) {
		String[] pool = switch (provider) {
			case CLAUDE -> CLAUDE_LINES;
			case OPENAI -> OPENAI_LINES;
			case GEMINI -> GEMINI_LINES;
			case FREE -> FREE_LINES;
		};
		return pool[RANDOM.nextInt(pool.length)];
	}
}
