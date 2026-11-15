# Architecture

## Overview

```
Player ──/ai commands──▶ AICommand ──▶ Brain.setForcedTask()
                                            │
Server tick ──▶ FakePlayerManager.tick() ──▶ Brain.tick()
                                            ├── Perception.scan()
                                            ├── decide() (utility AI + LLM)
                                            ├── currentTask.tick()
                                            └── MovementController.tick()
                                                  └── Pathfinder (A*)
```

## Components

### FakePlayerManager
Spawns `ServerPlayerEntity` fake players with a dummy `ClientConnection` + `ServerPlayNetworkHandler`, registers them via `PlayerManager.addPlayer()`, and ticks their brains. Handles summon/dismiss/respawn, one-AI-per-owner mapping, and join/leave broadcasts.

### Brain (utility AI)
- Every `brainTickInterval` ticks (default 10 = 0.5s), scores all 15 tasks via `Task.score()`.
- Urgent tasks (flee, eat-when-starving, combat-when-hit) interrupt immediately.
- Forced tasks (from `/ai` commands or chat) always win until complete.
- LLM planner (if configured) can suggest a task; it's only taken if its utility score is decent.
- Hysteresis (+5 to current task) prevents thrashing.
- Ticks the active task every server tick; catches exceptions so one bad task can't crash the server.

### Perception
Throttled to 2Hz. Scans entities via `World.getOtherEntities(box)` and blocks in a 32-block radius (capped at 4096 blocks for perf). Tracks hostiles, animals, drops, ores, logs, crops, beds, crafting tables, furnaces, chests, lava/fire, owner distance, night/rain, and a 0–3 danger level.

### Pathfinder (A*)
- A* over voxel grid, 14 neighbours (cardinal + diagonal + up/down + jump diagonals).
- Walkability: solid ground (or water/ladder/vine), 2 passable blocks at feet/head, lava/fire/cactus avoidance, diagonal corner-cut prevention.
- Cost: Euclidean + jump penalty + water/crop penalties.
- Heuristic: Euclidean distance.
- Path smoothing via line-of-sight checks.
- Fallbacks: greedy step, vanilla navigation, teleport-if-stuck (owner only).

### MovementController
Fake players have no client streaming movement packets, so instead of mob navigation the controller **steps the player's position directly** along the path (~0.215 blocks/tick at speed 1.0, i.e. normal walking pace). Every step:

1. Faces the direction of travel (yaw + head yaw + body yaw are synced to real clients).
2. Resolves a standable Y: flat walk, one-block step up, or a short drop.
3. Sets position + zero velocity and marks the AI on ground.

Stuck detection (no progress for 3s) recovers in stages: nudge up and replan → break the block in the way → teleport to the destination if close → give up on that leg.

### MiningUtil
Fake players can't drive `ServerPlayerInteractionManager` break progress, so `MiningUtil` keeps its own timer per block: hold for `hardness * 30 / breakingSpeed` ticks (the vanilla formula), swinging the arm each tick for the animation, then `world.breakBlock(pos, true, player)` so drops and stats behave normally. Unbreakable blocks (hardness < 0) are skipped.

### ChatListener
Hooks `ServerMessageEvents.CHAT_MESSAGE`. Messages addressed to the AI ("Aria, mine 10 iron", "hey ai follow me", "ai build a house") are converted into tasks by `NLPParser` immediately — so the AI reacts in the same tick — and the LLM (if configured) adds a personality reply asynchronously, hopping back to the server thread before touching game state.

### Tasks (15)
Each implements `score/start/tick/stop/isComplete/isUrgent`. See `ai/tasks/`:
`Follow`, `Mine`, `Chop`, `Build`, `Combat`, `Farm`, `Craft`, `Explore`, `Collect`, `Eat`, `Sleep`, `Flee`, `Guard`, `Idle`, `ReturnHome`.

### Memory
Per-AI JSON at `config/aicompanion/<name>_memory.json`: home, bed, chest, crafting table, furnace, death pos, waypoints, and stats (kills/mined/placed/collected/deaths). Resource locations are remembered in-session.

### LLM
- `LLMClient`: JDK `HttpClient` POST to OpenAI-compatible `/chat/completions`, Gson JSON, 15s timeout.
- `LLMPlanner`: sends perception + inventory snapshot, parses `{"task":...}` JSON into a concrete `Task`. Async with 15s cooldown so it never blocks the server tick. `chatReply()` powers `/ai say` personality.

### NLPParser
Rule-based parser for natural language ("mine 10 iron", "build a house", "attack that zombie"). Detects blocks, structures, crops, craftables, entities, and counts. Used when no LLM is configured (or as fallback).

### Blueprints
`Blueprint` = list of (offset, block). Built-ins: house (7×7 with roof/windows/door), tower, bridge, wall, farm, shelter, stairs, platform, pillar. `BuildTask` flattens ground, then places blocks one by one, skipping (with a warning) blocks it doesn't have.

## Threading / safety
- All game logic runs on the server thread (tick). LLM calls run on a `CompletableFuture` background thread and only *suggest* tasks — they never touch game state directly.
- Every task tick is wrapped in try/catch; a failing task is dropped, not fatal.
- Perception scan is capped (4096 blocks) and throttled (500ms) to protect TPS.

## Access widener
`ServerPlayerEntity.networkHandler` is attached to the fake player so it behaves like a connected player. It's made accessible via `src/main/resources/aicompanion.accesswidener` (declared in `fabric.mod.json` and wired up in `build.gradle`).

## Why fake players?
A real `ServerPlayerEntity` gets inventory, hunger, health, physics, collisions, and visibility for free — no custom entity code. The dummy connection means no real network traffic. This is the same technique used by Carpet, Meteor, and other fake-player mods.
