package com.aimobs.net;

import com.aimobs.AiMobs;
import net.minecraft.util.Identifier;

/**
 * Channel names only - deliberately kept free of any client or server classes
 * so it is safe to load on both sides.
 */
public final class AiChannels {

	public static final Identifier SPAWN = new Identifier(AiMobs.MOD_ID, "spawn");
	public static final Identifier COMMAND = new Identifier(AiMobs.MOD_ID, "command");
	public static final Identifier DISMISS = new Identifier(AiMobs.MOD_ID, "dismiss");
	public static final Identifier REQUEST_ROSTER = new Identifier(AiMobs.MOD_ID, "roster_request");
	public static final Identifier ROSTER = new Identifier(AiMobs.MOD_ID, "roster");

	private AiChannels() {
	}
}
