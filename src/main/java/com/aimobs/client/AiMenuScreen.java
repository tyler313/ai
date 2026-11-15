package com.aimobs.client;

import com.aimobs.ai.AiProvider;
import com.aimobs.config.AiConfig;
import com.aimobs.net.ClientSender;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Right Shift menu.
 *
 * Left half: pick a brain, paste its key, choose how many to spawn.
 * Right half: the AIs you own, with Follow / Stay / Come / Dismiss.
 */
@Environment(EnvType.CLIENT)
public class AiMenuScreen extends Screen {

	private static final int PANEL_W = 360;
	private static final int PANEL_H = 284;
	private static final int COL_W = 168;
	private static final int ROWS = 6;
	private static final int ROW_H = 22;

	// Kept across openings so the menu remembers where you were.
	private static AiProvider selectedProvider = AiProvider.CLAUDE;
	private static int spawnCount = 1;
	private static int selectedRosterIndex = 0;

	private int page;
	private int left;
	private int top;

	private final Map<AiProvider, TextFieldWidget> keyFields = new EnumMap<>(AiProvider.class);
	private final Map<AiProvider, TextFieldWidget> modelFields = new EnumMap<>(AiProvider.class);
	private final Map<AiProvider, ButtonWidget> providerButtons = new EnumMap<>(AiProvider.class);
	private final List<ButtonWidget> rosterButtons = new ArrayList<>();

	private ButtonWidget followButton;
	private ButtonWidget stayButton;
	private ButtonWidget comeButton;
	private ButtonWidget dismissButton;
	private ButtonWidget prevButton;
	private ButtonWidget nextButton;
	private ButtonWidget minusButton;
	private ButtonWidget plusButton;
	private ButtonWidget soloButton;

	public AiMenuScreen() {
		super(Text.literal("AI Command Center"));
	}

	@Override
	protected void init() {
		ClientSender.requestRoster();

		this.left = Math.max(6, (this.width - PANEL_W) / 2);
		this.top = Math.max(6, (this.height - PANEL_H) / 2);

		int lx = this.left + 10;
		int rx = this.left + 182;

		// ---- providers + their key/model fields -------------------------
		AiProvider[] providers = AiProvider.values();
		for (int i = 0; i < providers.length; i++) {
			AiProvider provider = providers[i];

			ButtonWidget button = ButtonWidget.builder(Text.literal(provider.getDisplayName()), b -> {
				selectedProvider = provider;
			}).dimensions(lx, this.top + 34 + i * 24, COL_W, 20).build();
			this.providerButtons.put(provider, button);
			this.addDrawableChild(button);

			TextFieldWidget key = new TextFieldWidget(this.textRenderer, lx, this.top + 140, COL_W, 18,
					Text.literal("API key"));
			key.setMaxLength(300);
			key.setText(AiConfig.getKey(provider));
			this.keyFields.put(provider, key);
			this.addDrawableChild(key);

			TextFieldWidget model = new TextFieldWidget(this.textRenderer, lx, this.top + 174, COL_W, 18,
					Text.literal("model"));
			model.setMaxLength(90);
			model.setText(AiConfig.getModel(provider));
			this.modelFields.put(provider, model);
			this.addDrawableChild(model);
		}

		// ---- spawn count + spawn button --------------------------------
		this.minusButton = ButtonWidget.builder(Text.literal("-"), b -> {
			spawnCount = Math.max(1, spawnCount - 1);
		}).dimensions(lx, this.top + 200, 22, 18).build();
		this.addDrawableChild(this.minusButton);

		this.plusButton = ButtonWidget.builder(Text.literal("+"), b -> {
			spawnCount = Math.min(8, spawnCount + 1);
		}).dimensions(lx + 46, this.top + 200, 22, 18).build();
		this.addDrawableChild(this.plusButton);

		ButtonWidget spawn = ButtonWidget.builder(Text.literal("SPAWN"), b -> {
			ClientSender.sendSpawn(selectedProvider, spawnCount);
		}).dimensions(lx, this.top + 228, COL_W, 20).build();
		this.addDrawableChild(spawn);

		// ---- roster ----------------------------------------------------
		for (int i = 0; i < ROWS; i++) {
			final int row = i;
			ButtonWidget button = ButtonWidget.builder(Text.literal("-"), b -> {
				selectedRosterIndex = this.page * ROWS + row;
			}).dimensions(rx, this.top + 46 + i * ROW_H, COL_W, 20).build();
			this.rosterButtons.add(button);
			this.addDrawableChild(button);
		}

		int bw = 82;
		this.followButton = ButtonWidget.builder(Text.literal("Follow me"),
				b -> this.sendToSelected("follow")).dimensions(rx, this.top + 182, bw, 20).build();
		this.addDrawableChild(this.followButton);

		this.stayButton = ButtonWidget.builder(Text.literal("Stay"),
				b -> this.sendToSelected("stay")).dimensions(rx + bw + 4, this.top + 182, bw, 20).build();
		this.addDrawableChild(this.stayButton);

		this.comeButton = ButtonWidget.builder(Text.literal("Come"),
				b -> this.sendToSelected("come")).dimensions(rx, this.top + 206, bw, 20).build();
		this.addDrawableChild(this.comeButton);

		this.dismissButton = ButtonWidget.builder(Text.literal("Dismiss"), b -> {
			AiRoster.Entry entry = this.selectedEntry();
			if (entry != null) {
				ClientSender.sendDismiss(entry.id());
			}
		}).dimensions(rx + bw + 4, this.top + 206, bw, 20).build();
		this.addDrawableChild(this.dismissButton);

		this.prevButton = ButtonWidget.builder(Text.literal("<"), b -> {
			this.page = Math.max(0, this.page - 1);
		}).dimensions(rx, this.top + 232, 24, 18).build();
		this.addDrawableChild(this.prevButton);

		this.nextButton = ButtonWidget.builder(Text.literal(">"), b -> {
			this.page = this.page + 1;
		}).dimensions(rx + COL_W - 24, this.top + 232, 24, 18).build();
		this.addDrawableChild(this.nextButton);

		this.soloButton = ButtonWidget.builder(Text.literal("Solo chat"), b -> {
			AiConfig.setSoloChat(!AiConfig.isSoloChat());
			AiConfig.save();
		}).dimensions(rx, this.top + 254, COL_W, 20).build();
		this.addDrawableChild(this.soloButton);
	}

	// ------------------------------------------------------------------

	private AiRoster.Entry selectedEntry() {
		List<AiRoster.Entry> roster = AiRoster.all();
		if (selectedRosterIndex < 0 || selectedRosterIndex >= roster.size()) {
			return null;
		}
		return roster.get(selectedRosterIndex);
	}

	private void sendToSelected(String command) {
		AiRoster.Entry entry = this.selectedEntry();
		if (entry != null) {
			ClientSender.sendCommands(entry.id(), "", List.of(command));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);

		// Panel
		context.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xE6121216);
		context.fill(this.left, this.top, this.left + PANEL_W, this.top + 1, 0xFF45454F);
		context.fill(this.left, this.top + PANEL_H - 1, this.left + PANEL_W, this.top + PANEL_H, 0xFF45454F);
		context.fill(this.left, this.top, this.left + 1, this.top + PANEL_H, 0xFF45454F);
		context.fill(this.left + PANEL_W - 1, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xFF45454F);
		context.fill(this.left + 179, this.top + 30, this.left + 180, this.top + PANEL_H - 12, 0x33FFFFFF);

		context.drawText(this.textRenderer, Text.literal("AI COMMAND CENTER"),
				this.left + 10, this.top + 8, 0xFF4FC3F7, false);
		context.drawText(this.textRenderer,
				Text.literal("Chat: <name> <command>    |    one AI out = just type"),
				this.left + 10, this.top + 20, 0xFF8A8A96, false);

		int lx = this.left + 10;
		int rx = this.left + 182;

		// Provider buttons: show whether a key is present.
		for (AiProvider provider : AiProvider.values()) {
			ButtonWidget button = this.providerButtons.get(provider);
			String suffix = provider.isKeyless()
					? "   (free, no key)"
					: (AiConfig.hasKey(provider) ? "   [key set]" : "   [no key]");
			button.setMessage(Text.literal((provider == selectedProvider ? "> " : "  ")
					+ provider.getDisplayName() + suffix));
		}

		// Only the selected provider's fields are visible.
		boolean keyless = selectedProvider.isKeyless();
		for (AiProvider provider : AiProvider.values()) {
			boolean visible = provider == selectedProvider && !keyless;
			this.keyFields.get(provider).setVisible(visible);
			this.modelFields.get(provider).setVisible(visible);
		}

		if (keyless) {
			context.drawText(this.textRenderer, Text.literal("Free AI needs no key at all."),
					lx, this.top + 132, 0xFF9E9E9E, false);
			context.drawText(this.textRenderer, Text.literal("It runs a small offline brain."),
					lx, this.top + 144, 0xFF9E9E9E, false);
			context.drawText(this.textRenderer, Text.literal("Same commands, no internet."),
					lx, this.top + 156, 0xFF9E9E9E, false);
		} else {
			context.drawText(this.textRenderer, Text.literal("API key - " + selectedProvider.getDisplayName()),
					lx, this.top + 130, 0xFFDDDDDD, false);
			context.drawText(this.textRenderer, Text.literal("Model"),
					lx, this.top + 164, 0xFFDDDDDD, false);
		}

		context.drawText(this.textRenderer, Text.literal("Spawn count"),
				lx, this.top + 190, 0xFFDDDDDD, false);
		context.drawText(this.textRenderer, Text.literal(String.valueOf(spawnCount)),
				lx + 28, this.top + 205, 0xFFFFFF66, false);
		context.drawText(this.textRenderer, Text.literal("Free AI needs no key"),
				lx, this.top + 254, 0xFF66666E, false);

		// Roster
		List<AiRoster.Entry> roster = AiRoster.all();
		int maxPage = Math.max(0, (roster.size() - 1) / ROWS);
		if (this.page > maxPage) {
			this.page = maxPage;
		}
		if (selectedRosterIndex >= roster.size()) {
			selectedRosterIndex = Math.max(0, roster.size() - 1);
		}

		context.drawText(this.textRenderer, Text.literal("Your AIs (" + roster.size() + ")"),
				rx, this.top + 34, 0xFF4FC3F7, false);

		for (int i = 0; i < ROWS; i++) {
			ButtonWidget button = this.rosterButtons.get(i);
			int absolute = this.page * ROWS + i;
			if (absolute >= roster.size()) {
				button.setMessage(Text.literal("-"));
				button.active = false;
			} else {
				AiRoster.Entry entry = roster.get(absolute);
				button.active = true;
				String prefix = absolute == selectedRosterIndex ? "> " : "  ";
				String follow = entry.following() ? " [F]" : "";
				button.setMessage(Text.literal(prefix + entry.name() + "  " + entry.status() + follow));
			}
		}

		boolean hasSelection = this.selectedEntry() != null;
		this.followButton.active = hasSelection;
		this.stayButton.active = hasSelection;
		this.comeButton.active = hasSelection;
		this.dismissButton.active = hasSelection;
		this.prevButton.active = this.page > 0;
		this.nextButton.active = this.page < maxPage;
		this.minusButton.active = spawnCount > 1;
		this.plusButton.active = spawnCount < 8;
		this.soloButton.setMessage(Text.literal("Solo chat: " + (AiConfig.isSoloChat() ? "ON" : "OFF")));

		if (!ClientSender.canTalkToServer()) {
			context.drawText(this.textRenderer, Text.literal("Server has no AI Mobs installed!"),
					this.left + 10, this.top + PANEL_H - 12, 0xFFFF5555, false);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		for (AiProvider provider : AiProvider.values()) {
			AiConfig.setKey(provider, this.keyFields.get(provider).getText());
			AiConfig.setModel(provider, this.modelFields.get(provider).getText());
		}
		AiConfig.save();
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
