package com.aicompanion.command;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Task;
import com.aicompanion.chat.NLPParser;
import com.aicompanion.player.AIPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * /ai command tree.
 *
 * <pre>
 * /ai summon [name]          — invite your AI companion
 * /ai dismiss                — send it away
 * /ai follow | stay          — follow you / stay put
 * /ai mine &lt;block&gt; [count]   — mine blocks (e.g. /ai mine diamond_ore 8)
 * /ai chop [count]           — chop trees
 * /ai build &lt;structure&gt;      — build house/tower/bridge/wall/farm/...
 * /ai attack                 — fight nearest hostile
 * /ai hunt                   — hunt animals for food
 * /ai farm [crop] [count]    — farm wheat/carrots/potatoes
 * /ai craft &lt;item&gt; [count]   — craft torches/sticks/planks/tools
 * /ai explore                — wander & discover
 * /ai guard                  — patrol & defend
 * /ai home                   — return to base
 * /ai eat | sleep            — eat / sleep
 * /ai say &lt;message&gt;          — talk to the AI (natural language)
 * /ai status                 — what is the AI doing?
 * /ai help                   — show help
 * </pre>
 */
public class AICommand {

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                net.minecraft.command.CommandManager.literal("ai")
                        .then(net.minecraft.command.CommandManager.literal("summon")
                                .executes(ctx -> summon(ctx.getSource(), null))
                                .then(net.minecraft.command.CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(net.minecraft.command.CommandManager.literal("dismiss")
                                .executes(ctx -> dismiss(ctx.getSource())))
                        .then(net.minecraft.command.CommandManager.literal("follow")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.followTask, "Following you!")))
                        .then(net.minecraft.command.CommandManager.literal("stay")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> {
                                    ai.brain.guardTask.guardAt(ai.pos(), 0);
                                    return ai.brain.guardTask;
                                }, "Staying put.")))
                        .then(net.minecraft.command.CommandManager.literal("mine")
                                .then(net.minecraft.command.CommandManager.argument("block", StringArgumentType.word())
                                        .executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "block"), 8))
                                        .then(net.minecraft.command.CommandManager.argument("count", IntegerArgumentType.integer(1, 256))
                                                .executes(ctx -> mine(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "block"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(net.minecraft.command.CommandManager.literal("chop")
                                .executes(ctx -> chop(ctx.getSource(), 16))
                                .then(net.minecraft.command.CommandManager.argument("count", IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> chop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(net.minecraft.command.CommandManager.literal("build")
                                .then(net.minecraft.command.CommandManager.argument("structure", StringArgumentType.word())
                                        .executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "structure")))))
                        .then(net.minecraft.command.CommandManager.literal("attack")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.combatTask, "Attacking!")))
                        .then(net.minecraft.command.CommandManager.literal("hunt")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.combatTask.huntAnimals(true), "Hunting animals!")))
                        .then(net.minecraft.command.CommandManager.literal("farm")
                                .executes(ctx -> farm(ctx.getSource(), "wheat", 32))
                                .then(net.minecraft.command.CommandManager.argument("crop", StringArgumentType.word())
                                        .executes(ctx -> farm(ctx.getSource(), StringArgumentType.getString(ctx, "crop"), 32))
                                        .then(net.minecraft.command.CommandManager.argument("count", IntegerArgumentType.integer(1, 256))
                                                .executes(ctx -> farm(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "crop"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(net.minecraft.command.CommandManager.literal("craft")
                                .then(net.minecraft.command.CommandManager.argument("item", StringArgumentType.word())
                                        .executes(ctx -> craft(ctx.getSource(), StringArgumentType.getString(ctx, "item"), 16))
                                        .then(net.minecraft.command.CommandManager.argument("count", IntegerArgumentType.integer(1, 256))
                                                .executes(ctx -> craft(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(net.minecraft.command.CommandManager.literal("explore")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.exploreTask, "Exploring!")))
                        .then(net.minecraft.command.CommandManager.literal("guard")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> {
                                    BlockPos c = ai.brain.perception.owner != null
                                            ? ai.brain.perception.owner.getBlockPos() : ai.memory.home;
                                    ai.brain.guardTask.guardAt(c, 12);
                                    return ai.brain.guardTask;
                                }, "Guarding!")))
                        .then(net.minecraft.command.CommandManager.literal("home")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.returnHomeTask, "Heading home.")))
                        .then(net.minecraft.command.CommandManager.literal("eat")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.eatTask, "Eating.")))
                        .then(net.minecraft.command.CommandManager.literal("sleep")
                                .executes(ctx -> setTask(ctx.getSource(), ai -> ai.brain.sleepTask, "Sleeping.")))
                        .then(net.minecraft.command.CommandManager.literal("say")
                                .then(net.minecraft.command.CommandManager.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> say(ctx.getSource(), StringArgumentType.getString(ctx, "message")))))
                        .then(net.minecraft.command.CommandManager.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(net.minecraft.command.CommandManager.literal("help")
                                .executes(ctx -> help(ctx.getSource())))
                        .executes(ctx -> help(ctx.getSource()))
        );
    }

    // ------------------------------------------------------------ handlers

    private static int summon(CommandSource src, String name) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.summon(src.getServer(), player, name);
        if (ai == null) {
            src.sendFeedback(() -> Text.literal("Failed to summon AI.").formatted(Formatting.RED), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Summoned " + ai.name() + "!").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int dismiss(CommandSource src) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        boolean ok = AIMod.FAKE_PLAYERS.dismissByOwner(player);
        if (ok) {
            src.sendFeedback(() -> Text.literal("AI dismissed.").formatted(Formatting.GREEN), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("No AI to dismiss. Use /ai summon first.").formatted(Formatting.RED), false);
        return 0;
    }

    private interface TaskFactory {
        Task create(AIPlayer ai);
    }

    private static int setTask(CommandSource src, TaskFactory factory, String confirm) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) {
            src.sendFeedback(() -> Text.literal("No AI summoned. Use /ai summon first.").formatted(Formatting.RED), false);
            return 0;
        }
        Task task = factory.create(ai);
        ai.brain.setForcedTask(task);
        ai.brain.sayNow(confirm);
        src.sendFeedback(() -> Text.literal(ai.name() + ": " + confirm).formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int mine(CommandSource src, String block, int count) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        ai.brain.setForcedTask(ai.brain.mineTask.configure(block, count));
        ai.brain.sayNow("Mining " + count + "x " + block + ".");
        return 1;
    }

    private static int chop(CommandSource src, int count) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        ai.brain.setForcedTask(ai.brain.chopTask.configure(count));
        ai.brain.sayNow("Chopping " + count + " logs.");
        return 1;
    }

    private static int build(CommandSource src, String structure) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        BlockPos anchor = player.getBlockPos().add(4, 0, 0);
        ai.brain.setForcedTask(ai.brain.buildTask.configure(structure, anchor));
        ai.brain.sayNow("Building a " + structure + " nearby.");
        return 1;
    }

    private static int farm(CommandSource src, String crop, int count) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        ai.brain.setForcedTask(ai.brain.farmTask.configure(crop, count));
        ai.brain.sayNow("Farming " + crop + ".");
        return 1;
    }

    private static int craft(CommandSource src, String item, int count) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        ai.brain.setForcedTask(ai.brain.craftTask.configure(item, count));
        ai.brain.sayNow("Crafting " + count + "x " + item + ".");
        return 1;
    }

    private static int say(CommandSource src, String message) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);

        // Act immediately with the deterministic parser (never blocks the tick).
        Task t = NLPParser.parse(ai.brain, message);
        if (t != null) {
            ai.brain.setForcedTask(t);
            ai.brain.sayNow("On it! (" + t.name() + ")");
        }

        // Then let the LLM add personality, asynchronously.
        if (AIMod.CONFIG.llmEnabled && ai.brain.llmPlanner.isAvailable()) {
            var server = src.getServer();
            ai.brain.llmPlanner.chatReplyAsync(message).thenAccept(reply -> {
                if (reply == null || reply.isBlank()) return;
                AIPlayer target = ai;
                server.execute(() -> target.brain.sayNow(reply.trim()));
            });
        } else if (t == null) {
            ai.brain.sayNow("I didn't understand that. Try /ai help for examples.");
        }
        return 1;
    }

    private static int status(CommandSource src) {
        ServerPlayerEntity player = getPlayer(src);
        if (player == null) return 0;
        AIPlayer ai = AIMod.FAKE_PLAYERS.forOwner(player);
        if (ai == null) return noAI(src);
        String s = ai.brain.statusString();
        src.sendFeedback(() -> Text.literal(ai.name() + " — " + s).formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int help(CommandSource src) {
        String help = """
                §6§lAI Companion§r — §7Your AI plays Minecraft with you!§r
                §e/ai summon [name]§r — invite your AI
                §e/ai dismiss§r — send it away
                §e/ai follow§r / §e/ai stay§r — follow you / stay put
                §e/ai mine <block> [count]§r — e.g. /ai mine diamond_ore 8
                §e/ai chop [count]§r — chop trees
                §e/ai build <structure>§r — house, tower, bridge, wall, farm, shelter, stairs, platform
                §e/ai attack§r / §e/ai hunt§r — fight mobs / hunt animals
                §e/ai farm [crop] [count]§r — wheat, carrots, potatoes, beetroots
                §e/ai craft <item> [count]§r — torch, sticks, oak_planks, crafting_table, tools
                §e/ai explore§r / §e/ai guard§r / §e/ai home§r
                §e/ai eat§r / §e/ai sleep§r
                §e/ai say <message>§r — talk naturally: "mine 10 iron", "build a house", "attack that zombie"
                §e/ai status§r — what is it doing?
                """;
        src.sendFeedback(() -> Text.literal(help), false);
        return 1;
    }

    private static ServerPlayerEntity getPlayer(CommandSource src) {
        try {
            return src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendFeedback(() -> Text.literal("Only players can use /ai commands.").formatted(Formatting.RED), false);
            return null;
        }
    }

    private static int noAI(CommandSource src) {
        src.sendFeedback(() -> Text.literal("No AI summoned. Use /ai summon first.").formatted(Formatting.RED), false);
        return 0;
    }
}
