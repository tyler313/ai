package com.aimobs.net;

import com.aimobs.ai.AiProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;

import java.util.List;

/** Client -> server messages. Only ever loaded on the client. */
@Environment(EnvType.CLIENT)
public final class ClientSender {

	private ClientSender() {
	}

	public static boolean canTalkToServer() {
		return ClientPlayNetworking.canSend(AiChannels.SPAWN);
	}

	public static void requestRoster() {
		send(AiChannels.REQUEST_ROSTER, new NbtCompound());
	}

	public static void sendSpawn(AiProvider provider, int count) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("provider", provider.getId());
		nbt.putInt("count", count);
		send(AiChannels.SPAWN, nbt);
	}

	public static void sendDismiss(int entityId) {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("id", entityId);
		send(AiChannels.DISMISS, nbt);
	}

	public static void sendCommands(int entityId, String say, List<String> commands) {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("id", entityId);
		nbt.putString("say", say == null ? "" : say);
		NbtList list = new NbtList();
		if (commands != null) {
			for (String command : commands) {
				list.add(NbtString.of(command));
			}
		}
		nbt.put("cmds", list);
		send(AiChannels.COMMAND, nbt);
	}

	private static void send(net.minecraft.util.Identifier channel, NbtCompound payload) {
		if (!ClientPlayNetworking.canSend(channel)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeNbt(payload);
		ClientPlayNetworking.send(channel, buf);
	}
}
