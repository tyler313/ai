package com.aimobs.ai;

/**
 * What a brain hands back for a chat message: something to say, plus a list of
 * commands in the tiny grammar defined by {@link TaskParser}.
 */
public record AiPlan(String say, java.util.List<String> commands) {

	public static AiPlan empty() {
		return new AiPlan("", java.util.List.of());
	}

	public boolean isEmpty() {
		return this.commands.isEmpty() && (this.say == null || this.say.isBlank());
	}
}
