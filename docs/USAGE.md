# Usage guide

## Getting started

1. Install the mod (see README).
2. Join a world and run `/ai summon` — Aria appears next to you and says hi.
3. Run `/ai help` for the full command list.
4. Try `/ai say follow me`, then `/ai say mine 8 iron`, then `/ai say build a house`.

## Commands

| Command | What it does |
|---|---|
| `/ai summon [name]` | Summon your AI (default name from config) |
| `/ai dismiss` | Remove your AI |
| `/ai follow` | Follow you |
| `/ai stay` | Stay put (guards current spot) |
| `/ai mine <block> [count]` | Mine blocks. Examples: `diamond_ore`, `iron_ore`, `coal_ore`, `stone`, `cobblestone`, `obsidian`, `ancient_debris` |
| `/ai chop [count]` | Chop trees for logs |
| `/ai build <structure>` | Build nearby: `house`, `tower`, `bridge`, `wall`, `farm`, `shelter`, `stairs`, `platform`, `pillar` |
| `/ai attack` | Fight nearest hostile |
| `/ai hunt` | Hunt animals for food |
| `/ai farm [crop] [count]` | Farm `wheat`, `carrots`, `potatoes`, `beetroots` |
| `/ai craft <item> [count]` | Craft `torch`, `sticks`, `oak_planks`, `crafting_table`, `wooden_pickaxe`, `stone_pickaxe`, `wooden_axe`, `stone_axe`, `wooden_sword`, `stone_sword` |
| `/ai explore` | Wander and discover |
| `/ai guard` | Patrol around you and defend |
| `/ai home` | Return to home base |
| `/ai eat` | Eat food |
| `/ai sleep` | Sleep in a bed (at night) |
| `/ai say <message>` | Natural language — see below |
| `/ai status` | Show current task + perception summary |

## Natural language (`/ai say`)

The AI understands everyday phrasing:

- **Follow:** "follow me", "come here", "stay with me"
- **Stay:** "stay", "stop", "wait here"
- **Mine:** "mine 10 iron", "get me some diamonds", "dig stone", "fetch coal"
- **Wood:** "chop wood", "get me wood", "cut down trees"
- **Build:** "build a house", "build a tower here", "make a bridge"
- **Fight:** "attack that zombie", "kill the skeleton", "hunt cows"
- **Farm:** "farm wheat", "harvest carrots", "plant potatoes"
- **Craft:** "craft torches", "make sticks", "create a crafting table"
- **Explore/guard:** "explore", "guard this place", "patrol"
- **Home:** "go home", "return to base"
- **Eat/sleep:** "eat", "I'm hungry", "sleep", "find a bed"

If an LLM is configured, `/ai say` also gets a personality reply in chat.

## Tips

- **Give it tools.** The AI equips the best pickaxe/axe/shovel/sword it has. A diamond pickaxe mines ores much faster than a hand.
- **Give it food.** It eats automatically when hungry (cooked beef > bread > apples...).
- **Give it blocks.** Building skips blocks it doesn't have — bring oak planks/cobblestone for houses/towers.
- **Set home.** `/ai home` returns to its spawn point. It remembers beds/chests it finds.
- **Park it safely.** `/ai stay` makes it guard in place — useful while you mine or build.
- **Check status.** `/ai status` shows exactly what it's doing and what it sees.
- **Multiple players.** Each player gets their own AI (one per owner). Name them differently: `/ai summon Bob`.

## Troubleshooting

| Problem | Fix |
|---|---|
| "No AI summoned" | Run `/ai summon` first |
| AI won't mine | Check `/ai status` — is it in danger (fleeing)? Give it a pickaxe. Is the block nearby? |
| AI won't build | It needs the blocks in inventory. Bring planks/cobblestone. |
| AI stuck | It auto-recovers (jump → dig → teleport). If truly stuck, `/ai dismiss` + `/ai summon` to reset. |
| LLM not working | Check `config/aicompanion.json` (`llmEnabled`, key, URL) or `AI_COMPANION_LLM_KEY` env var. Check server log for `[AI Companion] LLM` warnings. The AI still works without it. |
| AI died | It auto-respawns near you. |
