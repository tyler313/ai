# How to run AI Companion

Three ways, easiest first.

---

## Option 1 — Just play it (no Java, no build)

Every push to this repo builds the mod automatically. Grab the ready-made jar:

1. Open **https://github.com/tyler313/ai/actions** and click the newest green **build** run.
2. Scroll to **Artifacts** and download **`ai-companion`** (a `.zip`).
3. Unzip it — inside is `ai-companion-1.0.0.jar`.

Then install it into Minecraft (see [Install the mod](#install-the-mod) below).

---

## Option 2 — Build it yourself

You need **JDK 21** (Temurin/Adoptium, Microsoft OpenJDK, …) and an internet connection the
first time (Gradle downloads Minecraft + Fabric).

```bash
git clone https://github.com/tyler313/ai.git
cd ai
./gradlew build          # macOS / Linux
gradlew.bat build        # Windows
```

The mod lands in:

```
build/libs/ai-companion-1.0.0.jar     ← this is the one you install
build/libs/ai-companion-1.0.0-sources.jar
```

Check you're on Java 21 before building:

```bash
java -version    # should print 21.x
```

---

## Install the mod

1. **Install Fabric Loader for Minecraft 1.21.1** — https://fabricmc.net/use/installer
   (pick *Minecraft 1.21.1*, then *Install*). This creates a launcher profile called **fabric-loader-1.21.1**.
2. Find your `mods` folder:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
   (Create it if it doesn't exist.)
3. Put **two** jars in there:
   - `ai-companion-1.0.0.jar` (from Option 1 or 2)
   - [**Fabric API** for 1.21.1](https://modrinth.com/mod/fabric-api/versions?g=1.21.1) — required
4. Launch Minecraft with the **fabric-loader-1.21.1** profile. Mods run on the server side, so
   it works in singleplayer and on a Fabric server; clients joining a server don't need it.
5. Load a world and type in chat:

```
/ai summon
```

Aria spawns next to you and says hi. Try `/ai help`, then `/ai say mine 10 iron`.

> On a dedicated server, put both jars in the server's `mods/` folder instead. Nothing is
> needed on the players' clients.

---

## Option 3 — Run it from source (developer loop)

Loom provides ready-made run configurations that boot a Minecraft client/server with the mod
already loaded — no jar copying, hot-swappable with `./gradlew runClient` after each change.

```bash
./gradlew runClient     # launches Minecraft with the mod (singleplayer, /ai summon works)
./gradlew runServer     # launches a dev server on localhost:25565
```

First launch takes a while (Gradle downloads assets and runs the remapper).

**Dev server note:** Minecraft refuses to start until you accept the EULA. Run `./gradlew runServer`
once, then edit `run/eula.txt` and set `eula=true`, then run it again.

**Logs:** `run/logs/latest.log`. The mod logs under `[AI Companion]`.

**Reload after code changes:** stop, `./gradlew runClient` again (or use your IDE's Gradle
run config with hot swap). Config lives in `run/config/aicompanion.json`.

---

## First 5 minutes in game

```
/ai summon                  → your companion joins
/ai follow                  → follow me
/ai say mine 8 iron         → natural language works too
/ai build house             → builds a 7x7 house near you (needs ~200 planks/cobble)
/ai status                  → what is it doing right now?
/ai help                    → every command
```

Give it a pickaxe and some food — it equips the best tool it owns and eats when hungry.

---

## Turning on the LLM brain (optional)

Without it the AI is fully rule-based (and still plays fine). To add planning + personality,
edit `config/aicompanion.json` in your Minecraft folder (created on first launch):

```json
{
  "llmEnabled": true,
  "llmApiUrl": "https://api.openai.com/v1/chat/completions",
  "llmApiKey": "sk-...",
  "llmModel": "gpt-4o-mini"
}
```

Or set the environment variable `AI_COMPANION_LLM_KEY` (safer — the key is never written to disk).
Any OpenAI-compatible endpoint works, including Ollama:

```json
{ "llmApiUrl": "http://localhost:11434/v1/chat/completions", "llmModel": "llama3.1", "llmApiKey": "ollama" }
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Mod doesn't appear / `/ai` is unknown | Wrong Minecraft version (must be **1.21.1**), or Fabric API missing, or jar in the wrong `mods` folder. Check `logs/latest.log` for `[AI Companion]`. |
| Game crashes on startup | Make sure you're on **Java 21** and that you didn't mix API versions (1.21 Fabric API won't run on 1.21.1). |
| `./gradlew build` fails: `Unsupported class file major version` | Gradle is picking up an old JDK. Install JDK 21 and set `JAVA_HOME` to it. |
| `./gradlew build` hangs on first run | It's downloading Minecraft/mappings (~500 MB); needs internet. |
| `/ai summon` says "Failed to summon AI" | Another player already has an AI with that name — `/ai summon Steve` to use a different name. Check the log line it prints for the cause. |
| AI stands still | `/ai status` — it may be in danger or waiting; `/ai follow` to reset it. |
| AI died | It respawns next to you automatically. |
