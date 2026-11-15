package com.aimobs.config;

import com.aimobs.AiMobs;
import com.aimobs.ai.AiProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side settings: your API keys, the model names, and a couple of
 * preferences. Stored in plain text at {@code config/aimobs.json} - it is your
 * own computer, but do not share that file.
 */
public final class AiConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static JsonObject root = new JsonObject();
	private static Path file;

	private AiConfig() {
	}

	public static void load() {
		file = FabricLoader.getInstance().getConfigDir().resolve("aimobs.json");
		root = new JsonObject();
		try {
			if (Files.exists(file)) {
				JsonElement parsed = JsonParser.parseString(Files.readString(file));
				if (parsed.isJsonObject()) {
					root = parsed.getAsJsonObject();
				}
			}
		} catch (Exception e) {
			AiMobs.LOGGER.warn("Could not read config/aimobs.json, starting fresh", e);
		}
		section("keys");
		section("models");
		if (!root.has("soloChat")) {
			root.addProperty("soloChat", true);
		}
	}

	private static JsonObject section(String name) {
		if (!root.has(name) || !root.get(name).isJsonObject()) {
			root.add(name, new JsonObject());
		}
		return root.getAsJsonObject(name);
	}

	public static String getKey(AiProvider provider) {
		JsonElement el = section("keys").get(provider.getId());
		return el == null || el.isJsonNull() ? "" : el.getAsString();
	}

	public static void setKey(AiProvider provider, String value) {
		section("keys").addProperty(provider.getId(), value == null ? "" : value.trim());
	}

	public static boolean hasKey(AiProvider provider) {
		return !provider.isKeyless() && !getKey(provider).isBlank();
	}

	public static String getModel(AiProvider provider) {
		JsonElement el = section("models").get(provider.getId());
		String value = el == null || el.isJsonNull() ? "" : el.getAsString().trim();
		return value.isEmpty() ? provider.getDefaultModel() : value;
	}

	public static void setModel(AiProvider provider, String value) {
		section("models").addProperty(provider.getId(), value == null ? "" : value.trim());
	}

	public static boolean isSoloChat() {
		JsonElement el = root.get("soloChat");
		return el == null || el.isJsonNull() || el.getAsBoolean();
	}

	public static void setSoloChat(boolean value) {
		root.addProperty("soloChat", value);
	}

	public static void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root));
		} catch (Exception e) {
			AiMobs.LOGGER.warn("Could not save config/aimobs.json", e);
		}
	}
}
