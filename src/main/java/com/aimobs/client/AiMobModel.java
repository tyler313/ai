package com.aimobs.client;

import com.aimobs.entity.AiMobEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * A simple humanoid robot. It uses the standard 64x64 "player skin" layout,
 * which means any vanilla skin PNG works as a texture for these guys.
 */
@Environment(EnvType.CLIENT)
public class AiMobModel extends EntityModel<AiMobEntity> {

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public AiMobModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("head",
				ModelPartBuilder.create().uv(0, 0)
						.cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, new Dilation(0.0f)),
				ModelTransform.pivot(0.0f, 0.0f, 0.0f));

		root.addChild("body",
				ModelPartBuilder.create().uv(16, 16)
						.cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, new Dilation(0.0f)),
				ModelTransform.pivot(0.0f, 0.0f, 0.0f));

		root.addChild("right_arm",
				ModelPartBuilder.create().uv(40, 16)
						.cuboid(-2.0f, -2.0f, -2.0f, 4, 12, 4, new Dilation(0.0f)),
				ModelTransform.pivot(-5.5f, 2.0f, 0.0f));

		root.addChild("left_arm",
				ModelPartBuilder.create().uv(32, 48)
						.cuboid(-2.0f, -2.0f, -2.0f, 4, 12, 4, new Dilation(0.0f)),
				ModelTransform.pivot(5.5f, 2.0f, 0.0f));

		root.addChild("right_leg",
				ModelPartBuilder.create().uv(0, 16)
						.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, new Dilation(0.0f)),
				ModelTransform.pivot(-2.0f, 12.0f, 0.0f));

		root.addChild("left_leg",
				ModelPartBuilder.create().uv(16, 48)
						.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, new Dilation(0.0f)),
				ModelTransform.pivot(2.0f, 12.0f, 0.0f));

		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(AiMobEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		this.head.yaw = headYaw * 0.017453292f;
		this.head.pitch = headPitch * 0.017453292f;

		this.rightArm.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 2.0f * limbDistance * 0.5f;
		this.leftArm.pitch = MathHelper.cos(limbAngle * 0.6662f) * 2.0f * limbDistance * 0.5f;
		this.rightArm.roll = 0.0f;
		this.leftArm.roll = 0.0f;

		this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662f) * 1.4f * limbDistance * 0.5f;
		this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.4f * limbDistance * 0.5f;

		// A small idle bob so it does not look frozen.
		this.head.roll = MathHelper.sin(animationProgress * 0.05f) * 0.03f;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
					   float red, float green, float blue, float alpha) {
		this.root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
	}
}
