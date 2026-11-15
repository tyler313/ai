package com.aicompanion.config;

import com.aicompanion.AIMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON config for AI Companion.
 * Stored at {@code config/aicompanion.json}.
 */
public class AIConfig {
    // --- Identity ---
    public String aiName = "Aria";
    public String aiSkinUrl = ""; // optional, leave empty for default Steve skin

    // --- Behaviour ---
    public boolean autoFollow = true;
    public int followDistance = 3;
    public int teleportDistance = 64; // teleport if owner farther than this
    public boolean allowMining = true;
    public boolean allowBuilding = true;
    public boolean allowCombat = true;
    public boolean allowFarming = true;
    public boolean allowExploring = true;
    public boolean avoidLava = true;
    public boolean replantTrees = true;
    public boolean autoEat = true;
    public boolean autoSleep = true;
    public int perceptionRadius = 32;
    public int brainTickInterval = 10; // ticks between utility-AI decisions

    // --- LLM brain (optional) ---
    public boolean llmEnabled = false;
    public String llmApiUrl = "https://api.openai.com/v1/chat/completions";
    public String llmApiKey = ""; // env var AI_COMPANION_LLM_KEY takes precedence
    public String llmModel = "gpt-4o-mini";
    public int llmTimeoutSeconds = 15;
    public boolean llmChatPersonality = true;

    // --- Chat ---
    public boolean aiChatEnabled = true;
    public String chatPrefix = ""; // blank = auto "[<AI name>]"

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("aicompanion.json");

    public static AIConfig load() {
        AIConfig cfg = new AIConfig();
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                cfg.apply(obj);
                AIMod.LOGGER.info("[AI Companion] Loaded config from {}", CONFIG_PATH);
            } else {
                cfg.save();
                AIMod.LOGGER.info("[AI Companion] Created default config at {}", CONFIG_PATH);
            }
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Failed to load config, using defaults", e);
        }
        // Env override for API key (never commit keys to git)
        String envKey = System.getenv("AI_COMPANION_LLM_KEY");
        if (envKey != null && !envKey.isBlank()) {
            cfg.llmApiKey = envKey;
            cfg.llmEnabled = true;
        }
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(toJson()));
        } catch (IOException e) {
            AIMod.LOGGER.warn("[AI Companion] Failed to save config", e);
        }
    }

    private JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("aiName", aiName);
        o.addProperty("aiSkinUrl", aiSkinUrl);
        o.addProperty("autoFollow", autoFollow);
        o.addProperty("followDistance", followDistance);
        o.addProperty("teleportDistance", teleportDistance);
        o.addProperty("allowMining", allowMining);
        o.addProperty("allowBuilding", allowBuilding);
        o.addProperty("allowCombat", allowCombat);
        o.addProperty("allowFarming", allowFarming);
        o.addProperty("allowExploring", allowExploring);
        o.addProperty("avoidLava", avoidLava);
        o.addProperty("replantTrees", replantTrees);
        o.addProperty("autoEat", autoEat);
        o.addProperty("autoSleep", autoSleep);
        o.addProperty("perceptionRadius", perceptionRadius);
        o.addProperty("brainTickInterval", brainTickInterval);
        o.addProperty("llmEnabled", llmEnabled);
        o.addProperty("llmApiUrl", llmApiUrl);
        // Never persist a real API key to disk if it came from env — only save placeholder
        o.addProperty("llmApiKey", llmApiKey != null && llmApiKey.startsWith("sk-") ? "" : llmApiKey);
        o.addProperty("llmModel", llmModel);
        o.addProperty("llmTimeoutSeconds", llmTimeoutSeconds);
        o.addProperty("llmChatPersonality", llmChatPersonality);
        o.addProperty("aiChatEnabled", aiChatEnabled);
        o.addProperty("chatPrefix", chatPrefix);
        return o;
    }

    private void apply(JsonObject o) {
        if (o.has("aiName")) aiName = o.get("aiName").getAsString();
        if (o.has("aiSkinUrl")) aiSkinUrl = o.get("aiSkinUrl").getAsString();
        if (o.has("autoFollow")) autoFollow = o.get("autoFollow").getAsBoolean();
        if (o.has("followDistance")) followDistance = o.get("followDistance").getAsInt();
        if (o.has("teleportDistance")) teleportDistance = o.get("teleportDistance").getAsInt();
        if (o.has("allowMining")) allowMining = o.get("allowMining").getAsBoolean();
        if (o.has("allowBuilding")) allowBuilding = o.get("allowBuilding").getAsBoolean();
        if (o.has("allowCombat")) allowCombat = o.get("allowCombat").getAsBoolean();
        if (o.has("allowFarming")) allowFarming = o.get("allowFarming").getAsBoolean();
        if (o.has("allowExploring")) allowExploring = o.get("allowExploring").getAsBoolean();
        if (o.has("avoidLava")) avoidLava = o.get("avoidLava").getAsBoolean();
        if (o.has("replantTrees")) replantTrees = o.get("replantTrees").getAsBoolean();
        if (o.has("autoEat")) autoEat = o.get("autoEat").getAsBoolean();
        if (o.has("autoSleep")) autoSleep = o.get("autoSleep").getAsBoolean();
        if (o.has("perceptionRadius")) perceptionRadius = o.get("perceptionRadius").getAsInt();
        if (o.has("brainTickInterval")) brainTickInterval = o.get("brainTickInterval").getAsInt();
        if (o.has("llmEnabled")) llmEnabled = o.get("llmEnabled").getAsBoolean();
        if (o.has("llmApiUrl")) llmApiUrl = o.get("llmApiUrl").getAsString();
        if (o.has("llmApiKey")) llmApiKey = o.get("llmApiKey").getAsString();
        if (o.has("llmModel")) llmModel = o.get("llmModel").getAsString();
        if (o.has("llmTimeoutSeconds")) llmTimeoutSeconds = o.get("llmTimeoutSeconds").getAsInt();
        if (o.has("llmChatPersonality")) llmChatPersonality = o.get("llmChatPersonality").getAsBoolean();
        if (o.has("aiChatEnabled")) aiChatEnabled = o.get("aiChatEnabled").getAsBoolean();
        if (o.has("chatPrefix")) chatPrefix = o.get("chatPrefix").getAsString();
    }
}
