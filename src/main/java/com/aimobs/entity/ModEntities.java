package com.aimobs.entity;

import com.aimobs.AiMobs;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registration for the single entity type every AI uses. */
public final class ModEntities {

	public static EntityType<AiMobEntity> AI_MOB;

	private ModEntities() {
	}

	public static void register() {
		AI_MOB = Registry.register(
				Registries.ENTITY_TYPE,
				new Identifier(AiMobs.MOD_ID, "ai_mob"),
				FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, AiMobEntity::new)
						.dimensions(EntityDimensions.fixed(0.6f, 1.9f))
						.build()
		);
	}
}
