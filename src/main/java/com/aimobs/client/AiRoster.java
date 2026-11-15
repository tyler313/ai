package com.aimobs.client;

import com.aimobs.ai.AiProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The client's copy of "which AIs do I own right now". Synced by the server. */
@Environment(EnvType.CLIENT)
public final class AiRoster {

	public record Entry(int id, String name, String provider, String status,
						boolean following, int x, int y, int z) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private AiRoster() {
	}

	public static void set(List<Entry> entries) {
		ENTRIES.clear();
		ENTRIES.addAll(entries);
	}

	public static void clear() {
		ENTRIES.clear();
	}

	public static List<Entry> all() {
		return new ArrayList<>(ENTRIES);
	}

	public static int size() {
		return ENTRIES.size();
	}

	public static Entry byId(int id) {
		for (Entry entry : ENTRIES) {
			if (entry.id() == id) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * Find an AI by what the player typed: "Claude-2", "claude2", "@gemini1",
	 * or just "claude" when you only have one of that kind.
	 */
	public static Entry byName(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String needle = normalise(token);

		for (Entry entry : ENTRIES) {
			if (normalise(entry.name()).equals(needle)) {
				return entry;
			}
		}

		// Provider name alone, when unambiguous.
		AiProvider provider = null;
		for (AiProvider p : AiProvider.values()) {
			if (normalise(p.getDisplayName()).equals(needle) || normalise(p.getId()).equals(needle)) {
				provider = p;
				break;
			}
		}
		if (provider != null) {
			Entry found = null;
			for (Entry entry : ENTRIES) {
				if (AiProvider.byId(entry.provider()) == provider) {
					if (found != null) {
						return null; // ambiguous - the player has several of them
					}
					found = entry;
				}
			}
			return found;
		}
		return null;
	}

	private static String normalise(String value) {
		return value.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace("@", "").trim();
	}

	public static List<Entry> fromNbt(NbtCompound payload) {
		List<Entry> out = new ArrayList<>();
		NbtElement root = payload.get("list");
		if (root instanceof NbtList list) {
			for (NbtElement element : list) {
				if (!(element instanceof NbtCompound entry)) {
					continue;
				}
				out.add(new Entry(
						entry.getInt("id"),
						entry.getString("name"),
						entry.getString("provider"),
						entry.getString("status"),
						entry.getBoolean("follow"),
						entry.getInt("x"),
						entry.getInt("y"),
						entry.getInt("z")
				));
			}
		}
		return out;
	}
}
