# AI Mobs

Fabric mod for **Minecraft 1.20.1**, built against **Fabric Loader 0.20.1**.
Spawn AI companions into your world, plug in your own API keys (Claude / ChatGPT /
Gemini) — or use the **free AI that needs no key at all** — and boss them around by
typing in chat.

> **Status:** this repo builds green in CI (`./gradlew build`) *and* boots a real
> dedicated Minecraft 1.20.1 server with the mod loaded. The jar is attached to every
> successful Actions run as the `aimobs-jar` artifact.

Press **RIGHT SHIFT** in game to open the menu.

---

## What it does

* **Right Shift** opens the **AI Command Center**.
* Pick a brain: `Claude`, `ChatGPT`, `Gemini`, or `Free AI`.
* Paste the API key for that brain (the free one needs nothing).
* Choose **how many** of that model to spawn (`-` / `+`, up to 8 at once, 24 total).
* Hit **SPAWN** — robot-looking mobs appear next to you and follow you.
* Every AI gets a name: `Claude-1`, `ChatGPT-1`, `Gemini-2`, `Free AI-1`, ...

### Talking to them

Type in chat. If you have **one** AI out, just say what you want:

```
mine wood 8
guard
follow me
```

If you have **several** out, start with its name:

```
Claude-1 mine wood 8
@all follow me
gemini2 attack zombie
```

`@all <command>` sends it to every AI you own. Anything that is not addressed to an
AI goes to normal chat as usual.

### The menu's "follow" controls

Select an AI on the right side of the menu, then use:

| Button | What it does |
| --- | --- |
| **Follow me** | the AI walks with you (it teleports to you if you fly off) |
| **Stay** | it stops and waits |
| **Come** | it paths to you once |
| **Dismiss** | removes that AI from the world |

Toggle **Solo chat: ON/OFF** if you do not want all your chatting to go to your single AI.

---

## Commands the AI understands

| Command | Example | Notes |
| --- | --- | --- |
| `follow` | `follow me` | follows you around |
| `stay` | `stay here` | stops moving |
| `come` | `come here` | walks to you |
| `mine <block> <count>` | `mine iron_ore 16` | finds the nearest one within 16 blocks and digs it |
| `place <block> <count>` | `place dirt 6` | puts blocks on the ground in front of it |
| `attack <mob>` | `attack zombie` | hunts the nearest matching mob |
| `guard` | `guard this area` | attacks hostile mobs that come close |
| `wander` | `go explore` | roams around |
| `goto <x> <y> <z>` | `goto 100 64 -240` | walks to coordinates |
| `dance` / `jump` | `dance` | for the vibes |
| `say <text>` | `say hello` | makes it talk in chat |

Block names can be friendly words (`wood`, `stone`, `iron`, `diamond`, `sand`, ...),
short ids (`iron_ore`) or full ids (`minecraft:deepslate_iron_ore`). `#logs` style
block tags work too.

---

## The free AI (no key)

`Free AI` runs a small keyword-matching brain **inside the mod**. No account, no key,
no internet, no cost, instant replies. It handles every command in the table above
plus basic small talk. It is not a language model — it does not write essays — but it
always works, even offline, and it is the automatic fallback if a paid API call fails.

---

## API keys

| Brain | Where to get a key | Default model |
| --- | --- | --- |
| Claude | console.anthropic.com | `claude-sonnet-4-20250514` |
| ChatGPT | platform.openai.com | `gpt-4o-mini` |
| Gemini | aistudio.google.com | `gemini-2.0-flash` |

The model name is editable in the menu (the second text box), so if a model has been
retired just type the current one in.

Keys are saved **in plain text** to `.minecraft/config/aimobs.json`. They never leave
your computer except in the request to that provider — the mod does not send them
anywhere else, and the server you play on never sees them. Do not share that file.

The AI is asked to answer with a strict tiny JSON command list, so a model can never
do anything that is not one of the commands above.

---

## Installing

1. Install **Fabric Loader** (0.20.1 or newer) for **Minecraft 1.20.1**
   (fabricmc.net/use).
2. Put **Fabric API** for 1.20.1 in your `mods` folder.
3. Put `aimobs-1.0.0.jar` in your `mods` folder.
4. Launch the 1.20.1 Fabric profile.

The jar works on a **dedicated server too** — put it in the server's `mods` folder and
players can use the menu there as well (the AI entities live on the server, so it must
be installed on both sides for multiplayer).

## Building it yourself

Requires **JDK 17**:

```bash
./gradlew build
```

The jar lands in `build/libs/aimobs-1.0.0.jar` (ignore the `-sources` one).

There is also a GitHub Actions workflow (`.github/workflows/build.yml`) that runs on
every push/PR: it compiles the mod, uploads the jar as the **aimobs-jar** artifact, and
then boots a dedicated server with the mod installed to make sure it actually loads.

If you do not have a JDK installed, that is the easiest route: open the **Actions** tab,
click the latest green run, and download `aimobs-jar` from the *Artifacts* section.

To test in a dev world: `./gradlew runClient`.

---

## How it is put together

```
com.aimobs
├── AiMobs            common entrypoint: entity + attributes + server packets
├── AiMobsClient      client entrypoint: renderer, keybind, config
├── ai
│   ├── AiProvider    the four brains
│   ├── OfflineBrain  free keyword brain (text -> commands)
│   ├── AiApiClient   HTTP to Claude / OpenAI / Gemini (off the main thread)
│   ├── TaskParser    the one command grammar everybody shares
│   └── AiTask/AiPlan small data holders
├── entity
│   ├── ModEntities   entity registration
│   └── AiMobEntity   the body: pathfinding, mining, placing, fighting
├── client
│   ├── AiMenuScreen  the Right Shift UI
│   ├── ChatRouter    "<name> <command>" chat interception
│   ├── AiRoster      the client's list of your AIs
│   └── model/renderer/events
├── config/AiConfig   keys + models in config/aimobs.json
└── net               SPAWN / COMMAND / DISMISS / ROSTER packets (NBT payloads)
```

Design rule: **the brain never touches the world.** A brain (offline or LLM) only
produces a list of command strings; the server re-parses them with `TaskParser`,
checks you own the AI, and then the entity executes them. That keeps LLM output
harmless and means single-player and multiplayer behave identically.

---

## Known limitations

* The AI does not carry an inventory — mined blocks drop on the ground.
* Mining/placing has no animation or tool requirement; it just breaks/blocks the target.
* `place` puts blocks on open ground in front of the robot, it is not a builder.
* Very long conversations are not remembered; each message is judged on its own.
* If you rename a model version and the API returns 404, edit the **Model** box in the menu.

## Licence

MIT.
