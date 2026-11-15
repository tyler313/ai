package com.aicompanion.util;

import com.aicompanion.AIMod;
import com.aicompanion.player.AIPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Chat helpers: AI speaks in chat, system messages to owners.
 */
public class ChatUtil {

    /** AI speaks to everyone (visible in chat). */
    public static void sayAsAI(AIPlayer ai, String message) {
        ServerPlayerEntity p = ai.player;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        String prefix = AIMod.CONFIG.chatPrefix;
        if (prefix == null || prefix.isBlank()) prefix = "[" + ai.name() + "]";
        Text msg = Text.literal(prefix + " " + message).formatted(Formatting.AQUA);
        // Send to all players
        server.getPlayerManager().sendToAll(msg, false);
        // Also log
        AIMod.LOGGER.info("[AI:{}] {}", ai.name(), message);
    }

    /** Private message to a single player. */
    public static void tell(ServerPlayerEntity player, String message, Formatting color) {
        if (player == null) return;
        player.sendMessage(Text.literal(message).formatted(color), false);
    }

    /** AI speaks privately to its owner. */
    public static void sayToOwner(AIPlayer ai, String message) {
        if (ai.ownerUuid == null) {
            sayAsAI(ai, message);
            return;
        }
        ServerPlayerEntity owner = ai.player.getServer().getPlayerManager().getPlayer(ai.ownerUuid);
        if (owner != null) {
            String prefix = AIMod.CONFIG.chatPrefix;
            owner.sendMessage(Text.literal(prefix + " " + message).formatted(Formatting.AQUA), false);
        } else {
            sayAsAI(ai, message);
        }
    }
}
