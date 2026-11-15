package com.aimobs;

import com.aimobs.entity.AiMobEntity;
import com.aimobs.entity.ModEntities;
import com.aimobs.net.ServerAiHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entrypoint for AI Mobs.
 *
 * The mod works in single player out of the box. For multiplayer, this jar has to be
 * installed on the server as well, because the AI entities live on the server.
 */
public class AiMobs implements ModInitializer {

	public static final String MOD_ID = "aimobs";
	public static final Logger LOGGER = LoggerFactory.getLogger("AI Mobs");

	/** How many AI mobs a single player may own at once. */
	public static final int MAX_AI_PER_PLAYER = 24;

	@Override
	public void onInitialize() {
		ModEntities.register();
		FabricDefaultAttributeRegistry.register(ModEntities.AI_MOB, AiMobEntity.createAttributes());
		ServerAiHandler.register();

		LOGGER.info("AI Mobs loaded - press RIGHT SHIFT in game to open the AI menu.");
	}
}
