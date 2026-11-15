package com.aimobs.client;

import com.aimobs.AiMobs;
import com.aimobs.ai.AiProvider;
import com.aimobs.entity.AiMobEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class AiMobRenderer extends MobEntityRenderer<AiMobEntity, AiMobModel> {

	private static final Identifier TEX_CLAUDE = texture("claude");
	private static final Identifier TEX_OPENAI = texture("openai");
	private static final Identifier TEX_GEMINI = texture("gemini");
	private static final Identifier TEX_FREE = texture("free");

	public AiMobRenderer(EntityRendererFactory.Context context) {
		super(context, new AiMobModel(context.getPart(ModModelLayers.AI_MOB)), 0.5f);
	}

	private static Identifier texture(String name) {
		return new Identifier(AiMobs.MOD_ID, "textures/entity/ai_mob_" + name + ".png");
	}

	@Override
	public Identifier getTexture(AiMobEntity entity) {
		return switch (AiProvider.byId(entity.getProviderKey())) {
			case CLAUDE -> TEX_CLAUDE;
			case OPENAI -> TEX_OPENAI;
			case GEMINI -> TEX_GEMINI;
			case FREE -> TEX_FREE;
		};
	}
}
