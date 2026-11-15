package com.aimobs.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Talks to the real LLM APIs. Runs entirely off-thread (java.net.http) and
 * hands the result back through the supplied {@link Executor} so callers can
 * safely touch Minecraft state on the client thread.
 *
 * Only {@link AiProvider#CLAUDE}, {@link AiProvider#OPENAI} and
 * {@link AiProvider#GEMINI} are handled here - the free provider never leaves
 * the machine.
 */
public final class AiApiClient {

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "aimobs-http");
		t.setDaemon(true);
		return t;
	});

	private AiApiClient() {
	}

	/** The contract we ask every model to follow. */
	private static String systemPrompt(String aiName) {
		return """
				You are "%s", a friendly robot companion living inside a Minecraft world.
				The player types short instructions to you in chat. You must reply with STRICT JSON only.
				No markdown, no code fences, no explanation outside the JSON.

				JSON shape:
				{"say": "one short in-character sentence, max 90 characters", "commands": ["command", "command"]}

				Allowed commands (use at most 3):
				follow | stay | come | guard | wander | jump | dance
				goto <x> <y> <z>
				mine <block> <count>      (count 1-64)
				place <block> <count>     (count 1-32)
				attack <mob>              (e.g. zombie, skeleton, creeper, cow, monster)
				say <text>

				Block arguments must be block ids such as minecraft:oak_log, minecraft:stone,
				minecraft:iron_ore, or the tag #logs. Never use any command outside this list.
				If you cannot do something, say so in "say" and return an empty commands list.
				""".formatted(aiName);
	}

	/**
	 * Fire off a request. Callbacks always run on the given executor.
	 */
	public static void request(AiProvider provider, String apiKey, String model, String aiName,
							   String userText, Executor callbackExecutor,
							   Consumer<AiPlan> onSuccess, Consumer<String> onError) {
		POOL.submit(() -> {
			try {
				String raw = switch (provider) {
					case CLAUDE -> callClaude(apiKey, model, systemPrompt(aiName), userText);
					case OPENAI -> callOpenAI(apiKey, model, systemPrompt(aiName), userText);
					case GEMINI -> callGemini(apiKey, model, systemPrompt(aiName), userText);
					case FREE -> throw new IllegalStateException("The free provider never uses HTTP");
				};
				AiPlan plan = parsePlan(raw);
				callbackExecutor.execute(() -> onSuccess.accept(plan));
			} catch (Throwable t) {
				String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				callbackExecutor.execute(() -> onError.accept(message));
			}
		});
	}

	// ------------------------------------------------------------------
	// Providers
	// ------------------------------------------------------------------

	private static String callClaude(String key, String model, String system, String user) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", 400);
		body.addProperty("system", system);
		JsonArray messages = new JsonArray();
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.addProperty("content", user);
		messages.add(msg);
		body.add("messages", messages);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
				.timeout(Duration.ofSeconds(30))
				.header("content-type", "application/json")
				.header("x-api-key", key)
				.header("anthropic-version", "2023-06-01")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();

		JsonObject json = send(request);
		JsonArray content = json.getAsJsonArray("content");
		if (content == null || content.isEmpty()) {
			throw new IllegalStateException("Claude returned no content");
		}
		StringBuilder sb = new StringBuilder();
		for (JsonElement el : content) {
			JsonObject part = el.getAsJsonObject();
			if (part.has("text")) {
				sb.append(part.get("text").getAsString());
			}
		}
		return sb.toString();
	}

	private static String callOpenAI(String key, String model, String system, String user) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", 400);
		JsonArray messages = new JsonArray();
		JsonObject sys = new JsonObject();
		sys.addProperty("role", "system");
		sys.addProperty("content", system);
		messages.add(sys);
		JsonObject msg = new JsonObject();
		msg.addProperty("role", "user");
		msg.addProperty("content", user);
		messages.add(msg);
		body.add("messages", messages);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
				.timeout(Duration.ofSeconds(30))
				.header("content-type", "application/json")
				.header("authorization", "Bearer " + key)
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();

		JsonObject json = send(request);
		JsonArray choices = json.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			throw new IllegalStateException("ChatGPT returned no choices");
		}
		return choices.get(0).getAsJsonObject()
				.getAsJsonObject("message")
				.get("content").getAsString();
	}

	private static String callGemini(String key, String model, String system, String user) throws Exception {
		JsonObject body = new JsonObject();

		JsonObject systemInstruction = new JsonObject();
		JsonArray sysParts = new JsonArray();
		JsonObject sysPart = new JsonObject();
		sysPart.addProperty("text", system);
		sysParts.add(sysPart);
		systemInstruction.add("parts", sysParts);
		body.add("system_instruction", systemInstruction);

		JsonArray contents = new JsonArray();
		JsonObject content = new JsonObject();
		JsonArray parts = new JsonArray();
		JsonObject part = new JsonObject();
		part.addProperty("text", user);
		parts.add(part);
		content.add("parts", parts);
		content.addProperty("role", "user");
		contents.add(content);
		body.add("contents", contents);

		JsonObject generationConfig = new JsonObject();
		generationConfig.addProperty("maxOutputTokens", 400);
		generationConfig.addProperty("temperature", 0.6);
		body.add("generationConfig", generationConfig);

		String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
				+ ":generateContent?key=" + key;

		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("content-type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();

		JsonObject json = send(request);
		JsonArray candidates = json.getAsJsonArray("candidates");
		if (candidates == null || candidates.isEmpty()) {
			throw new IllegalStateException("Gemini returned no candidates");
		}
		JsonArray outParts = candidates.get(0).getAsJsonObject()
				.getAsJsonObject("content")
				.getAsJsonArray("parts");
		if (outParts == null || outParts.isEmpty()) {
			throw new IllegalStateException("Gemini returned no text");
		}
		return outParts.get(0).getAsJsonObject().get("text").getAsString();
	}

	private static JsonObject send(HttpRequest request) throws Exception {
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		int code = response.statusCode();
		String responseBody = response.body();
		if (code < 200 || code >= 300) {
			String hint = switch (code) {
				case 401, 403 -> " (bad or missing API key)";
				case 429 -> " (rate limited / out of credit)";
				case 400 -> " (bad request - check the model name in the menu)";
				default -> "";
			};
			throw new IllegalStateException("HTTP " + code + hint);
		}
		if (responseBody == null || responseBody.isBlank()) {
			throw new IllegalStateException("Empty response");
		}
		JsonElement parsed = JsonParser.parseString(responseBody);
		if (!parsed.isJsonObject()) {
			throw new IllegalStateException("Unexpected response format");
		}
		return parsed.getAsJsonObject();
	}

	// ------------------------------------------------------------------
	// Plan parsing
	// ------------------------------------------------------------------

	/** Parse {@code {"say": "...", "commands": [...]}} out of whatever the model said. */
	public static AiPlan parsePlan(String raw) {
		if (raw == null) {
			return AiPlan.empty();
		}
		String text = raw.trim();
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start >= 0 && end > start) {
			String jsonSlice = text.substring(start, end + 1);
			try {
				JsonObject obj = JsonParser.parseString(jsonSlice).getAsJsonObject();
				String say = obj.has("say") && !obj.get("say").isJsonNull()
						? obj.get("say").getAsString()
						: "";
				List<String> commands = new ArrayList<>();
				if (obj.has("commands") && obj.get("commands").isJsonArray()) {
					for (JsonElement el : obj.getAsJsonArray("commands")) {
						if (!el.isJsonNull()) {
							String command = el.getAsString().trim();
							if (!command.isEmpty() && TaskParser.parse(command) != null) {
								commands.add(command);
							}
						}
					}
				}
				return new AiPlan(say, commands);
			} catch (RuntimeException ignored) {
				// Not valid JSON - fall through and just say the text.
			}
		}
		// Model ignored the format: keep its words, do nothing.
		String say = text.replace("\n", " ").trim();
		if (say.length() > 120) {
			say = say.substring(0, 120);
		}
		return new AiPlan(say, List.of());
	}
}
