package com.aicompanion.ai.llm;

import com.aicompanion.AIMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal OpenAI-compatible chat client (JDK HttpClient, no extra deps).
 * Works with OpenAI, OpenRouter, Ollama (OpenAI mode), LM Studio, etc.
 */
public class LLMClient {
    private final HttpClient http;
    private final Gson gson = new Gson();

    public LLMClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return AIMod.CONFIG.llmEnabled
                && AIMod.CONFIG.llmApiKey != null && !AIMod.CONFIG.llmApiKey.isBlank()
                && AIMod.CONFIG.llmApiUrl != null && !AIMod.CONFIG.llmApiUrl.isBlank();
    }

    /**
     * Send a chat completion request. Returns the assistant message content, or null on failure.
     * Runs synchronously — call from a background thread if you need non-blocking behaviour.
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!isConfigured()) return null;
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", AIMod.CONFIG.llmModel);
            body.addProperty("temperature", 0.7);
            body.addProperty("max_tokens", 512);
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemPrompt);
            messages.add(sys);
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userPrompt);
            messages.add(user);
            body.add("messages", messages);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AIMod.CONFIG.llmApiUrl))
                    .timeout(Duration.ofSeconds(AIMod.CONFIG.llmTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + AIMod.CONFIG.llmApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                AIMod.LOGGER.warn("[AI Companion] LLM API error {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject msg = first.getAsJsonObject("message");
            return msg.get("content").getAsString();
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] LLM request failed: {}", e.getMessage());
            return null;
        }
    }

    /** Async variant — completes with null on failure. */
    public CompletableFuture<String> chatAsync(String systemPrompt, String userPrompt) {
        return CompletableFuture.supplyAsync(() -> chat(systemPrompt, userPrompt));
    }
}
