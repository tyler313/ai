# AI Companion — a Minecraft AI that plays with you

A **Fabric mod for Minecraft 1.21.1** where an AI joins your world as a real player and plays alongside you. It follows you, mines, chops, builds, farms, fights, explores, guards, eats, sleeps — and talks back in chat.

Powered by **utility AI + A\* pathfinding**, with an **optional LLM brain** (OpenAI-compatible) for smarter planning and conversation. No LLM? It still plays great with the built-in rule-based brain.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green) ![Fabric](https://img.shields.io/badge/Fabric-Loader-0.16+-blue) ![Java](https://img.shields.io/badge/Java-21-orange) ![build](https://github.com/tyler313/ai/actions/workflows/build.yml/badge.svg)

> ✅ Compiles against Yarn 1.21.1 mappings — the CI workflow builds the mod on every push and uploads the jar.

---

## ✨ What it does

| Skill | Description |
|---|---|
| **Follow** | Follows you everywhere, jumps, swims, teleports if you get too far |
| **Mine** | Finds ores/blocks, paths to them, mines (vanilla break timing) with the best tool, avoids lava pockets |
| **Chop** | Chops trees for logs, replants saplings |
| **Build** | Builds houses, towers, bridges, walls, farms, shelters, stairs, platforms |
| **Combat** | Fights zombies/skeletons/etc. with sword or bow, strafes, retreats when hurt |
| **Farm** | Tills, plants, harvests, and replants wheat/carrots/potatoes/beetroots |
| **Craft** | Crafts planks, sticks, torches, crafting tables, and basic tools |
| **Explore** | Wanders and discovers new areas |
| **Guard** | Patrols an area and defends it |
| **Eat / Sleep** | Eats when hungry, sleeps in a bed at night |
| **Flee** | Runs from lava, fire, or overwhelming enemies |
| **Collect** | Picks up dropped items |
| **Chat** | Talks in chat, understands natural language ("mine 10 iron", "build a house") |

### How smart is it?

- **Utility AI brain** — every 0.5s it scores all 15 tasks based on hunger, health, danger, distance to you, time of day, and nearby resources, then picks the best one (with hysteresis so it doesn't flip-flop).
- **A\* pathfinder** — 3D voxel pathfinding with jumping, swimming, climbing, lava avoidance, path smoothing, stuck detection, and recovery (step up → dig → teleport).
- **Talks to you** — say "Aria, mine 10 iron" in chat and it just does it (rule-based, instant; optional LLM adds personality).
- **Perception** — scans a 32-block radius for ores, logs, crops, beds, hostiles, drops, and you.
- **Memory** — remembers home base, beds, chests, crafting tables, waypoints, and stats. Saved to disk, survives restarts.
- **LLM brain (optional)** — plug in any OpenAI-compatible API (OpenAI, OpenRouter, Ollama, LM Studio) and the AI gets high-level planning + personality. Falls back to rules if the API is down.

---

## 🚀 Quick start

### Requirements

- Minecraft **1.21.1**
- [Fabric Loader 0.16+](https://fabricmc.net/use/) + [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
- Java **21**

### Install

1. Install Fabric Loader for 1.21.1 (see above).
2. Drop `ai-companion-1.0.0.jar` (from `build/libs/`) into your `mods/` folder.
3. Launch Minecraft, join/create a world.
4. Type in chat:
   ```
   /ai summon
   ```
   Your AI companion joins as a real player (default name: **Aria**).

### First commands

```
/ai help                          — show all commands
/ai follow                        — follow me
/ai mine diamond_ore 8            — mine 8 diamond ore
/ai chop 16                       — chop 16 logs
/ai build house                   — build a house nearby
/ai say mine 10 iron              — talk naturally!
/ai status                        — what is it doing?
```

Try natural language:
```
/ai say follow me
/ai say build a tower here
/ai say attack that zombie
/ai say get me wood
/ai say farm wheat
/ai say explore
```

---

## 🧠 LLM brain (optional, makes it smarter)

By default the AI uses the built-in utility-AI brain (no API key needed). To enable LLM planning + chat:

1. Edit `config/aicompanion.json` (created on first launch):
   ```json
   {
     "llmEnabled": true,
     "llmApiUrl": "https://api.openai.com/v1/chat/completions",
     "llmApiKey": "sk-...",
     "llmModel": "gpt-4o-mini"
   }
   ```
   Or set the env var `AI_COMPANION_LLM_KEY` (recommended — never commit keys).
2. Restart the server / reload.

Works with: OpenAI, OpenRouter, Ollama (`http://localhost:11434/v1/chat/completions`), LM Studio, and any OpenAI-compatible endpoint.

> The LLM only suggests high-level plans (e.g. "mine diamond_ore x8"). Movement, mining, combat, and building are all handled by the deterministic game code — so even if the LLM is slow or offline, the AI still plays well.

---

## 🔨 Build from source

```bash
# Requires Java 21 + internet (downloads Minecraft, Fabric, deps)
./gradlew build

# Jar lands in build/libs/ai-companion-1.0.0.jar
```

> **Note:** If `gradle/wrapper/gradle-wrapper.jar` is missing (fresh clone), generate it once with system Gradle:
> ```bash
> gradle wrapper --gradle-version 8.8
> ./gradlew build
> ```
> Or just use system Gradle directly: `gradle build`.

### Run in dev

```bash
./gradlew runServer   # starts a dev server with the mod
./gradlew runClient   # starts a dev client
```

---

## ⚙️ Config

`config/aicompanion.json`:

| Key | Default | Meaning |
|---|---|---|
| `aiName` | `"Aria"` | Name of the AI player |
| `autoFollow` | `true` | Follow owner by default |
| `followDistance` | `3` | How close to stay |
| `teleportDistance` | `64` | Teleport if owner farther than this |
| `allowMining` / `allowBuilding` / `allowCombat` / `allowFarming` / `allowExploring` | `true` | Enable/disable skills |
| `avoidLava` | `true` | Avoid lava/fire/cactus |
| `replantTrees` | `true` | Replant saplings after chopping |
| `autoEat` / `autoSleep` | `true` | Eat/sleep automatically |
| `perceptionRadius` | `32` | How far the AI can "see" |
| `llmEnabled` / `llmApiUrl` / `llmApiKey` / `llmModel` | — | LLM brain (see above) |

---

## 📁 Project structure

```
src/main/java/com/aicompanion/
├── AIMod.java                  — mod entry, tick loop, lifecycle
├── config/AIConfig.java        — JSON config
├── player/
│   ├── FakePlayerManager.java  — spawns fake-player AIs
│   └── AIPlayer.java           — AI wrapper (player + brain + path state)
├── ai/
│   ├── Brain.java              — utility AI + task scheduler
│   ├── Perception.java         — sees blocks/entities/dangers
│   ├── Memory.java             — home, waypoints, stats (persisted)
│   ├── Pathfinder.java         — A* voxel pathfinding
│   ├── MovementController.java — follows paths, stuck recovery
│   ├── Task.java               — task interface
│   ├── tasks/                  — 15 skills (follow, mine, chop, build, ...)
│   └── llm/                    — LLM client + planner
├── build/                      — structure blueprints
├── command/AICommand.java      — /ai command tree
├── chat/                       — chat listener + natural-language parser
└── util/                       — block/inventory/chat helpers
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/USAGE.md](docs/USAGE.md) for details.

---

## ❓ FAQ

**Is it a real player?**
Yes — it's a `ServerPlayerEntity` with a dummy connection. It appears in the tab list, has inventory/health/hunger, can be seen and hit by other players, and interacts with the world like a human.

**Does it need an API key?**
No. The built-in brain works offline. An LLM key just makes planning + chat smarter.

**Can it play on servers?**
Yes — it's a server-side mod. Install it on the server (or singleplayer). Clients don't need it.

**Will it grief / mine my base?**
It only mines/builds where you tell it to (or nearby resources when idle). It avoids lava and doesn't mine bedrock. Use `/ai stay` to park it.

**How do I change its name?**
`/ai summon Steve` or set `aiName` in config.

**Does it look natural while moving?**
It walks at human speed, turns to face where it's going, and swings its arm when mining/attacking. Because fake players have no client feeding inputs, movement is driven by position updates rather than WASD — it walks and steps up blocks, but doesn't perfectly use ladders or parkour.

**Known limitations**
- No automatic armor equipping, villager trading, or netherite progression yet.
- Building places blocks directly (no scaffolding towers), so bring materials.
- Ladder/vine climbing is pathable but not flawless.
- LLM planning needs an API key; without one it runs fully rule-based (still plays well).

---

## 📄 License

MIT — see [LICENSE](LICENSE).
