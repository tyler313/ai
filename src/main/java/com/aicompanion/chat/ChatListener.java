package com.aicompanion.chat;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Task;
import com.aicompanion.player.AIPlayer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

/**
 * Listens to public chat and lets players talk to their AI naturally.
 *
 * <p>Anything a player says that is addressed to the AI — "Aria, mine some iron",
 * "hey ai follow me", "ai build a house" — is parsed and turned into a task.
 * If an LLM is configured, the AI also replies in character (asynchronously, so
 * the server thread is never blocked).
 */
public final class ChatListener {

    private ChatListener() {
    }

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                handleChat(sender, message.getContent().getString());
            } catch (Exception e) {
                AIMod.LOGGER.warn("[AI Companion] Chat handling failed", e);
            }
        });
    }

    private static void handleChat(ServerPlayerEntity sender, String raw) {
        if (sender == null || raw == null) return;
        if (!AIMod.CONFIG.aiChatEnabled) return;

        // Which AI is this? Prefer the sender's own companion.
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(sender);
        if (ai == null) ai = AIMod.FAKE_PLAYERS.byName(AIMod.CONFIG.aiName);
        if (ai == null) return;

        String name = ai.name();
        if (!NLPParser.isAddressedToAI(name, raw)) return;

        String instruction = stripAddress(name, raw).trim();
        if (instruction.isEmpty()) {
            ai.brain.sayNow("Yes? Tell me what to do — e.g. \"mine 10 iron\" or \"build a house\".");
            return;
        }

        // 1. Act immediately with the deterministic parser (instant response).
        Task task = NLPParser.parse(ai.brain, instruction);
        if (task != null) {
            ai.brain.setForcedTask(task);
            ai.brain.sayNow("On it — " + task.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
        }

        // 2. Let the LLM add personality / handle free-form chat, off-thread.
        final AIPlayer companion = ai;
        if (AIMod.CONFIG.llmEnabled && companion.brain.llmPlanner.isAvailable()) {
            final String prompt = sender.getName().getString() + " says: " + instruction;
            MinecraftServer server = sender.getServer();
            if (server != null) {
                companion.brain.llmPlanner.chatReplyAsync(prompt).thenAccept(reply -> {
                    if (reply == null || reply.isBlank()) return;
                    final String clean = reply.trim();
                    // Hop back onto the server thread before touching game state.
                    server.execute(() -> {
                        if (task == null) {
                            companion.brain.sayNow(clean);
                        } else {
                            companion.brain.say(clean);
                        }
                    });
                });
            }
        } else if (task == null) {
            ai.brain.sayNow("I didn't catch that. Try \"mine 10 iron\", \"chop wood\", "
                    + "\"build a house\", \"attack that zombie\", or \"follow me\".");
        }
    }

    /** Remove a leading "Aria," / "hey ai," / "ai:" style address from a message. */
    private static String stripAddress(String aiName, String raw) {
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] prefixes = {
                aiName.toLowerCase(Locale.ROOT),
                "hey " + aiName.toLowerCase(Locale.ROOT),
                "yo " + aiName.toLowerCase(Locale.ROOT),
                "hey ai", "yo ai", "ai"
        };
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String rest = text.substring(prefix.length());
                rest = rest.replaceFirst("^[,:;\\-\\s]+", "");
                return rest;
            }
        }
        return text;
    }
}
