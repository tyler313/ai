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
        Text msg = Text.literal(prefix(ai) + " " + message).formatted(Formatting.AQUA);
        // Send to all players
        server.getPlayerManager().sendToAll(msg, false);
        // Also log
        AIMod.LOGGER.info("[AI:{}] {}", ai.name(), message);
    }

    /** Chat prefix for this AI — config value if set, else "[<name>] ". */
    private static String prefix(AIPlayer ai) {
        String p = AIMod.CONFIG.chatPrefix;
        if (p == null || p.isBlank()) return "[" + ai.name() + "]";
        return p;
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
            owner.sendMessage(Text.literal(prefix(ai) + " " + message).formatted(Formatting.AQUA), false);
        } else {
            sayAsAI(ai, message);
        }
    }
}
