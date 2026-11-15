package com.aimobs;

import com.aimobs.client.AiMobModel;
import com.aimobs.client.AiMobRenderer;
import com.aimobs.client.ClientEvents;
import com.aimobs.client.ModModelLayers;
import com.aimobs.config.AiConfig;
import com.aimobs.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AiMobsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		AiConfig.load();

		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.AI_MOB, AiMobModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.AI_MOB, AiMobRenderer::new);

		ClientEvents.register();

		AiMobs.LOGGER.info("AI Mobs client ready - RIGHT SHIFT opens the menu.");
	}
}
