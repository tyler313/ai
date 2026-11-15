package com.aimobs.ai;

import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * A single job for an AI mob. Everything an AI does is reduced to one of these,
 * which keeps the "brain" (text -> commands) completely separate from the
 * "body" (commands -> block breaking / pathfinding).
 */
public final class AiTask {

	public enum Kind {
		FOLLOW, STAY, COME, GOTO, MINE, PLACE, ATTACK, GUARD, WANDER, SAY, JUMP, DANCE
	}

	public final Kind kind;
	/** Block spec / mob name / message, depending on {@link #kind}. */
	public final String arg;
	/** How many blocks to mine or place. Mutable because it counts down. */
	public int count;
	public final BlockPos pos;

	public AiTask(Kind kind, String arg, int count, BlockPos pos) {
		this.kind = kind;
		this.arg = arg;
		this.count = count;
		this.pos = pos;
	}

	public String describe() {
		return switch (this.kind) {
			case MINE -> "mining " + this.arg + " (" + this.count + " left)";
			case PLACE -> "placing " + this.arg + " (" + this.count + " left)";
			case ATTACK -> "hunting " + this.arg;
			case FOLLOW -> "following you";
			case STAY -> "waiting";
			case COME -> "coming to you";
			case GOTO -> "walking to " + this.pos;
			case GUARD -> "guarding";
			case WANDER -> "exploring";
			case DANCE -> "dancing";
			case JUMP -> "hopping";
			case SAY -> "talking";
		};
	}

	@Override
	public String toString() {
		return this.kind.name().toLowerCase(Locale.ROOT) + (this.arg == null ? "" : " " + this.arg);
	}
}
