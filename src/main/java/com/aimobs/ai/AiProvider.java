package com.aimobs.ai;

import java.util.Locale;

/**
 * The four "brains" you can put inside an AI mob.
 *
 * FREE is the built-in offline brain: no account, no key, no internet.
 * The other three talk to a real LLM API using the key you paste in the menu.
 */
public enum AiProvider {

	CLAUDE("claude", "Claude", "claude-sonnet-4-20250514", 0xFFD97757),
	OPENAI("openai", "ChatGPT", "gpt-4o-mini", 0xFF10A37F),
	GEMINI("gemini", "Gemini", "gemini-2.0-flash", 0xFF4285F4),
	FREE("free", "Free AI", "offline-brain-v1", 0xFF9E9E9E);

	private final String id;
	private final String displayName;
	private final String defaultModel;
	private final int colour;

	AiProvider(String id, String displayName, String defaultModel, int colour) {
		this.id = id;
		this.displayName = displayName;
		this.defaultModel = defaultModel;
		this.colour = colour;
	}

	public String getId() {
		return this.id;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getDefaultModel() {
		return this.defaultModel;
	}

	/** ARGB colour used for menu highlighting. */
	public int getColour() {
		return this.colour;
	}

	/** True when this provider never needs an API key. */
	public boolean isKeyless() {
		return this == FREE;
	}

	public static AiProvider byId(String id) {
		if (id != null) {
			String needle = id.toLowerCase(Locale.ROOT);
			for (AiProvider p : values()) {
				if (p.id.equals(needle)) {
					return p;
				}
			}
		}
		return FREE;
	}
}
