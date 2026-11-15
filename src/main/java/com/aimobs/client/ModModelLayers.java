package com.aimobs.client;

import com.aimobs.AiMobs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class ModModelLayers {

	public static final EntityModelLayer AI_MOB =
			new EntityModelLayer(new Identifier(AiMobs.MOD_ID, "ai_mob"), "main");

	private ModModelLayers() {
	}
}
