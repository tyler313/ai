package com.aimobs.client;

import com.aimobs.net.AiChannels;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Keybind, chat hook and roster packets. */
@Environment(EnvType.CLIENT)
public final class ClientEvents {

	private static KeyBinding menuKey;

	private ClientEvents() {
	}

	public static void register() {
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.aimobs.menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"key.categories.aimobs"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null && client.player != null) {
					client.setScreen(new AiMenuScreen());
				}
			}
		});

		// Chat: "<name> <command>" goes to that AI instead of the server.
		ClientSendMessageEvents.ALLOW_CHAT.register(ChatRouter::onChat);

		ClientPlayNetworking.registerGlobalReceiver(AiChannels.ROSTER, (client, handler, buf, sender) -> {
			NbtCompound nbt = buf.readNbt();
			if (nbt == null) {
				return;
			}
			List<AiRoster.Entry> entries = AiRoster.fromNbt(nbt);
			client.execute(() -> AiRoster.set(entries));
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientSender.requestRoster());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				AiRoster.clear());
	}
}
