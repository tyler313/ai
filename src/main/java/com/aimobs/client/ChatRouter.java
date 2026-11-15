package com.aimobs.client;

import com.aimobs.ai.AiApiClient;
import com.aimobs.ai.AiPlan;
import com.aimobs.ai.AiProvider;
import com.aimobs.ai.OfflineBrain;
import com.aimobs.config.AiConfig;
import com.aimobs.net.ClientSender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Turns what you type in chat into AI instructions.
 *
 * Rules:
 *   "Claude-1 mine wood 8"  -> that specific AI
 *   "@all follow me"        -> every AI you own
 *   "mine wood 8"           -> your only AI (if you own exactly one)
 *   anything else           -> normal chat
 */
@Environment(EnvType.CLIENT)
public final class ChatRouter {

	private ChatRouter() {
	}

	/** @return true when the message should also be sent to the server as normal chat. */
	public static boolean onChat(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return true;
		}

		String msg = message == null ? "" : message.trim();
		if (msg.isEmpty() || msg.startsWith("/")) {
			return true;
		}

		List<AiRoster.Entry> roster = AiRoster.all();
		if (roster.isEmpty()) {
			return true;
		}

		String lower = msg.toLowerCase(Locale.ROOT);

		// Broadcast
		if (lower.startsWith("@all ") || lower.startsWith("@everyone ") || lower.startsWith("all ais ")) {
			String body = msg.substring(msg.indexOf(' ') + 1).trim();
			for (AiRoster.Entry entry : roster) {
				dispatch(client, entry, body);
			}
			info("Sent to all " + roster.size() + " AIs.");
			return false;
		}

		// Specific AI by name
		String[] parts = msg.split("\\s+", 2);
		AiRoster.Entry named = AiRoster.byName(parts[0]);
		if (named != null && parts.length > 1) {
			dispatch(client, named, parts[1]);
			return false;
		}

		// Only one AI out? Then plain chat is for it.
		if (roster.size() == 1 && AiConfig.isSoloChat()) {
			dispatch(client, roster.get(0), msg);
			return false;
		}

		return true;
	}

	private static void dispatch(MinecraftClient client, AiRoster.Entry entry, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		AiProvider provider = AiProvider.byId(entry.provider());

		// Free AI, or a paid one without a key: use the built-in brain.
		if (provider.isKeyless() || !AiConfig.hasKey(provider)) {
			if (!provider.isKeyless()) {
				info(entry.name() + " has no " + provider.getDisplayName()
						+ " key yet - using the offline brain. (Right Shift to add one)");
			}
			AiPlan plan = OfflineBrain.plan(provider, text);
			ClientSender.sendCommands(entry.id(), plan.say(), plan.commands());
			return;
		}

		String key = AiConfig.getKey(provider);
		String model = AiConfig.getModel(provider);

		AiApiClient.request(provider, key, model, entry.name(), text, client,
				plan -> ClientSender.sendCommands(entry.id(), plan.say(), plan.commands()),
				error -> {
					info(entry.name() + ": " + provider.getDisplayName() + " failed (" + error
							+ ") - falling back to the offline brain.");
					AiPlan fallback = OfflineBrain.plan(provider, text);
					ClientSender.sendCommands(entry.id(), fallback.say(), fallback.commands());
				});
	}

	public static void info(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.inGameHud == null) {
			return;
		}
		client.inGameHud.getChatHud().addMessage(Text.literal("§8[§bAI Mobs§8] §7" + message));
	}
}
