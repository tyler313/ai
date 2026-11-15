package com.aimobs.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The command grammar shared by every brain and by the server that executes the
 * commands. Keeping this tiny means an LLM can never do anything we did not
 * explicitly allow:
 *
 * <pre>
 *   follow | stay | come | guard | wander | jump | dance
 *   goto &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *   mine &lt;block&gt; &lt;count&gt;
 *   place &lt;block&gt; &lt;count&gt;
 *   attack &lt;mob&gt;
 *   say &lt;text&gt;
 * </pre>
 */
public final class TaskParser {

	private TaskParser() {
	}

	public static AiTask parse(String raw) {
		if (raw == null) {
			return null;
		}
		String line = raw.trim();
		if (line.isEmpty()) {
			return null;
		}
		String[] t = line.split("\\s+");
		String head = t[0].toLowerCase(Locale.ROOT);

		switch (head) {
			case "follow":
				return new AiTask(AiTask.Kind.FOLLOW, null, 0, null);
			case "stay":
			case "stop":
			case "wait":
			case "hold":
				return new AiTask(AiTask.Kind.STAY, null, 0, null);
			case "come":
				return new AiTask(AiTask.Kind.COME, null, 0, null);
			case "guard":
				return new AiTask(AiTask.Kind.GUARD, null, 0, null);
			case "wander":
			case "explore":
				return new AiTask(AiTask.Kind.WANDER, null, 0, null);
			case "jump":
				return new AiTask(AiTask.Kind.JUMP, null, 0, null);
			case "dance":
				return new AiTask(AiTask.Kind.DANCE, null, 0, null);
			case "goto": {
				if (t.length >= 4) {
					try {
						return new AiTask(AiTask.Kind.GOTO, null, 0, new BlockPos(
								Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])));
					} catch (NumberFormatException ignored) {
						return null;
					}
				}
				return null;
			}
			case "mine":
			case "dig":
			case "break":
			case "chop": {
				String spec = t.length > 1 ? t[1] : (head.equals("chop") ? "#logs" : "minecraft:stone");
				if (head.equals("chop")) {
					spec = "#logs";
				}
				int count = t.length > 2 ? intOr(t[2], 8) : 8;
				return new AiTask(AiTask.Kind.MINE, spec, clamp(count, 1, 64), null);
			}
			case "place":
			case "build": {
				String spec = t.length > 1 ? t[1] : "minecraft:dirt";
				int count = t.length > 2 ? intOr(t[2], 1) : 1;
				return new AiTask(AiTask.Kind.PLACE, spec, clamp(count, 1, 32), null);
			}
			case "attack":
			case "kill":
			case "fight": {
				String target = t.length > 1 ? t[1] : "monster";
				return new AiTask(AiTask.Kind.ATTACK, target, 0, null);
			}
			case "say": {
				String msg = line.length() > 3 ? line.substring(3).trim() : "";
				return new AiTask(AiTask.Kind.SAY, msg, 0, null);
			}
			default:
				return null;
		}
	}

	private static int intOr(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ------------------------------------------------------------------
	// Blocks
	// ------------------------------------------------------------------

	/**
	 * Turn a block spec into a list of block ids. Accepts plain ids
	 * ("minecraft:iron_ore"), short names ("iron_ore") and friendly words
	 * ("iron", "wood", "stone", ...).
	 */
	public static List<Identifier> candidateIds(String spec) {
		List<Identifier> out = new ArrayList<>(2);
		String s = spec.toLowerCase(Locale.ROOT).trim();

		switch (s) {
			case "wood", "log", "logs", "tree", "trees", "timber" -> {
				out.add(new Identifier("minecraft", "oak_log"));
				out.add(new Identifier("minecraft", "spruce_log"));
				out.add(new Identifier("minecraft", "birch_log"));
				out.add(new Identifier("minecraft", "jungle_log"));
				out.add(new Identifier("minecraft", "acacia_log"));
				out.add(new Identifier("minecraft", "dark_oak_log"));
			}
			case "stone", "rock" -> {
				out.add(new Identifier("minecraft", "stone"));
				out.add(new Identifier("minecraft", "deepslate"));
				out.add(new Identifier("minecraft", "andesite"));
				out.add(new Identifier("minecraft", "diorite"));
				out.add(new Identifier("minecraft", "granite"));
				out.add(new Identifier("minecraft", "tuff"));
			}
			case "cobble", "cobblestone" -> out.add(new Identifier("minecraft", "cobblestone"));
			case "dirt" -> {
				out.add(new Identifier("minecraft", "dirt"));
				out.add(new Identifier("minecraft", "coarse_dirt"));
			}
			case "grass" -> out.add(new Identifier("minecraft", "grass_block"));
			case "sand" -> {
				out.add(new Identifier("minecraft", "sand"));
				out.add(new Identifier("minecraft", "red_sand"));
			}
			case "gravel" -> out.add(new Identifier("minecraft", "gravel"));
			case "clay" -> out.add(new Identifier("minecraft", "clay"));
			case "snow" -> out.add(new Identifier("minecraft", "snow_block"));
			case "ice" -> out.add(new Identifier("minecraft", "ice"));
			case "obsidian" -> out.add(new Identifier("minecraft", "obsidian"));
			case "coal" -> addOres(out, "coal_ore", "deepslate_coal_ore");
			case "iron" -> addOres(out, "iron_ore", "deepslate_iron_ore");
			case "copper" -> addOres(out, "copper_ore", "deepslate_copper_ore");
			case "gold" -> addOres(out, "gold_ore", "deepslate_gold_ore", "nether_gold_ore");
			case "redstone" -> addOres(out, "redstone_ore", "deepslate_redstone_ore");
			case "lapis", "lazuli" -> addOres(out, "lapis_ore", "deepslate_lapis_ore");
			case "diamond" -> addOres(out, "diamond_ore", "deepslate_diamond_ore");
			case "emerald" -> addOres(out, "emerald_ore", "deepslate_emerald_ore");
			case "quartz" -> addOres(out, "nether_quartz_ore");
			case "ancient_debris", "netherite" -> addOres(out, "ancient_debris");
			case "leaves", "leaf" -> {
				out.add(new Identifier("minecraft", "oak_leaves"));
				out.add(new Identifier("minecraft", "birch_leaves"));
				out.add(new Identifier("minecraft", "spruce_leaves"));
			}
			case "planks", "plank", "wood_planks" -> out.add(new Identifier("minecraft", "oak_planks"));
			default -> {
				if (s.contains(":")) {
					out.add(new Identifier(s));
				} else {
					out.add(new Identifier("minecraft", s));
				}
			}
		}
		return out;
	}

	private static void addOres(List<Identifier> out, String... paths) {
		for (String p : paths) {
			out.add(new Identifier("minecraft", p));
		}
	}

	/** Does this block state count as the thing the AI was told to mine? */
	public static boolean matches(BlockState state, String spec) {
		if (state == null || state.isAir() || spec == null) {
			return false;
		}
		String s = spec.toLowerCase(Locale.ROOT).trim();

		// Tag specs, e.g. "#logs" or "#minecraft:logs".
		if (s.startsWith("#")) {
			String tagName = s.substring(1);
			Identifier tagId = tagName.contains(":")
					? new Identifier(tagName)
					: new Identifier("minecraft", tagName);
			TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, tagId);
			return state.isIn(tag);
		}

		for (Identifier id : candidateIds(s)) {
			Block block = Registries.BLOCK.get(id);
			if (block != null && block != Blocks.AIR && state.isOf(block)) {
				return true;
			}
		}
		return false;
	}

	/** Best block to place for a spec (falls back to dirt). */
	public static Block resolveBlock(String spec) {
		if (spec != null) {
			String s = spec.toLowerCase(Locale.ROOT).trim();
			if (s.equals("wood") || s.equals("log") || s.equals("logs") || s.equals("tree")) {
				return Blocks.OAK_PLANKS;
			}
			for (Identifier id : candidateIds(s)) {
				Block block = Registries.BLOCK.get(id);
				if (block != null && block != Blocks.AIR) {
					return block;
				}
			}
		}
		return Blocks.DIRT;
	}
}
