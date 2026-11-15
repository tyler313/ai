package com.aicompanion;

import com.aicompanion.command.AICommand;
import com.aicompanion.chat.ChatListener;
import com.aicompanion.config.AIConfig;
import com.aicompanion.player.FakePlayerManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI Companion — main mod initializer.
 *
 * <p>Registers commands, server tick loop, and lifecycle hooks.
 * The AI itself lives in {@link FakePlayerManager} + {@code com.aicompanion.ai.Brain}.
 */
public class AIMod implements ModInitializer {
    public static final String MOD_ID = "aicompanion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static AIConfig CONFIG;
    public static FakePlayerManager FAKE_PLAYERS;

    @Override
    public void onInitialize() {
        CONFIG = AIConfig.load();
        FAKE_PLAYERS = new FakePlayerManager();

        // Commands: /ai ...
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AICommand.register(dispatcher);
        });

        // Main AI tick loop — runs every server tick, brains decide every N ticks.
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Chat: players can just talk to their companion ("Aria, mine 10 iron").
        ChatListener.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("[AI Companion] Server started. AI ready — use /ai summon to invite your companion.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[AI Companion] Server stopping, saving AI state...");
            if (FAKE_PLAYERS != null) {
                FAKE_PLAYERS.saveAll();
            }
            if (CONFIG != null) {
                CONFIG.save();
            }
        });

        LOGGER.info("[AI Companion] Initialized. LLM enabled: {}, name: {}",
                CONFIG.llmEnabled, CONFIG.aiName);
    }

    private void onServerTick(MinecraftServer server) {
        if (FAKE_PLAYERS == null) return;
        try {
            FAKE_PLAYERS.tick(server);
        } catch (Exception e) {
            LOGGER.error("[AI Companion] Tick error", e);
        }
    }
}
