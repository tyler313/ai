package com.aimobs.net;

import com.aimobs.AiMobs;
import com.aimobs.ai.AiProvider;
import com.aimobs.entity.AiMobEntity;
import com.aimobs.entity.ModEntities;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the menu: spawning, dismissing and forwarding commands.
 * Everything a client asks for is re-validated here (ownership, limits,
 * command grammar), so a modified client cannot do anything silly.
 */
public final class ServerAiHandler {

	private ServerAiHandler() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(AiChannels.SPAWN, (server, player, handler, buf, sender) -> {
			NbtCompound nbt = buf.readNbt();
			if (nbt == null) {
				return;
			}
			String providerId = nbt.getString("provider");
			int count = MathHelper.clamp(nbt.getInt("count"), 1, 8);
			server.execute(() -> spawn(player, providerId, count));
		});

		ServerPlayNetworking.registerGlobalReceiver(AiChannels.COMMAND, (server, player, handler, buf, sender) -> {
			NbtCompound nbt = buf.readNbt();
			if (nbt == null) {
				return;
			}
			int entityId = nbt.getInt("id");
			String say = nbt.getString("say");
			List<String> commands = new ArrayList<>();
			NbtElement cmds = nbt.get("cmds");
			if (cmds instanceof NbtList list) {
				for (NbtElement element : list) {
					commands.add(element.asString());
				}
			}
			server.execute(() -> {
				if (!(player.getWorld() instanceof ServerWorld world)) {
					return;
				}
				Entity entity = world.getEntityById(entityId);
				if (entity instanceof AiMobEntity ai && isOwnedBy(ai, player.getUuid())) {
					ai.onCommands(say, commands);
					sendRoster(world, player);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(AiChannels.DISMISS, (server, player, handler, buf, sender) -> {
			NbtCompound nbt = buf.readNbt();
			if (nbt == null) {
				return;
			}
			int entityId = nbt.getInt("id");
			server.execute(() -> {
				if (!(player.getWorld() instanceof ServerWorld world)) {
					return;
				}
				Entity entity = world.getEntityById(entityId);
				if (entity instanceof AiMobEntity ai && isOwnedBy(ai, player.getUuid())) {
					ai.discard();
					sendRoster(world, player);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(AiChannels.REQUEST_ROSTER, (server, player, handler, buf, sender) ->
				server.execute(() -> {
					if (player.getWorld() instanceof ServerWorld world) {
						sendRoster(world, player);
					}
				}));
	}

	// ------------------------------------------------------------------

	private static boolean isOwnedBy(AiMobEntity ai, UUID uuid) {
		return uuid.equals(ai.getOwnerUuid());
	}

	private static void spawn(ServerPlayerEntity player, String providerId, int count) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		AiProvider provider = AiProvider.byId(providerId);

		List<AiMobEntity> owned = ownedAis(world, player.getUuid());
		int room = AiMobs.MAX_AI_PER_PLAYER - owned.size();
		if (room <= 0) {
			player.sendMessage(Text.literal("You already have " + AiMobs.MAX_AI_PER_PLAYER
					+ " AIs - dismiss some with Right Shift first."), false);
			sendRoster(world, player);
			return;
		}

		Set<String> usedNames = new HashSet<>();
		for (AiMobEntity ai : owned) {
			usedNames.add(ai.getName().getString());
		}

		int spawned = 0;
		int attempts = Math.min(count, room);
		for (int i = 0; i < attempts; i++) {
			AiMobEntity ai = ModEntities.AI_MOB.create(world);
			if (ai == null) {
				continue;
			}

			String name = nextName(provider, usedNames);
			usedNames.add(name);

			double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
			double x = player.getX() + Math.cos(angle) * 2.0;
			double z = player.getZ() + Math.sin(angle) * 2.0;

			ai.refreshPositionAndAngles(x, player.getY(), z, player.getYaw(), 0.0f);
			ai.setProviderKey(provider.getId());
			ai.setOwner(player);
			ai.setCustomName(Text.literal(name));
			ai.setCustomNameVisible(true);
			ai.setMode(AiMobEntity.Mode.FOLLOW);
			ai.setHealth(20.0f);

			if (world.spawnEntity(ai)) {
				spawned++;
			}
		}

		if (spawned > 0) {
			player.sendMessage(Text.literal("Spawned " + spawned + " x " + provider.getDisplayName()
					+ ". Type in chat: <name> <what to do>"), false);
		}
		sendRoster(world, player);
	}

	private static String nextName(AiProvider provider, Set<String> used) {
		String base = provider.getDisplayName();
		for (int i = 1; i < 1000; i++) {
			String candidate = base + "-" + i;
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
		return base + "-" + System.currentTimeMillis() % 1000;
	}

	private static List<AiMobEntity> ownedAis(ServerWorld world, UUID owner) {
		List<AiMobEntity> list = new ArrayList<>();
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof AiMobEntity ai && owner.equals(ai.getOwnerUuid()) && !ai.isRemoved()) {
				list.add(ai);
			}
		}
		return list;
	}

	private static void sendRoster(ServerWorld world, ServerPlayerEntity player) {
		NbtList list = new NbtList();
		for (AiMobEntity ai : ownedAis(world, player.getUuid())) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("id", ai.getId());
			entry.putString("name", ai.getName().getString());
			entry.putString("provider", ai.getProviderKey());
			entry.putString("status", ai.describeState());
			entry.putBoolean("follow", ai.isFollowing());
			entry.putInt("x", ai.getBlockPos().getX());
			entry.putInt("y", ai.getBlockPos().getY());
			entry.putInt("z", ai.getBlockPos().getZ());
			list.add(entry);
		}

		NbtCompound payload = new NbtCompound();
		payload.put("list", list);

		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeNbt(payload);
		ServerPlayNetworking.send(player, AiChannels.ROSTER, buf);
	}
}
