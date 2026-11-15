package com.aicompanion.ai.tasks;

import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Craft items from raw materials (planks, sticks, torches, tools).
 * Uses direct inventory manipulation (no crafting-table GUI needed for basic recipes).
 */
public class CraftTask implements Task {
    public String targetItem = "torch";
    public int targetCount = 16;
    public int crafted = 0;

    @Override public String name() { return "Craft " + targetItem; }

    @Override public double score(Brain brain) {
        // Only when forced, or when we have materials for something useful
        return 0;
    }

    @Override public void start(Brain brain) {
        crafted = 0;
        brain.say("Crafting " + targetCount + "x " + targetItem + ".");
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        // Craft one batch per tick (throttled by brain tick rate)
        boolean ok = craftOne(brain, targetItem);
        if (ok) crafted++;
        else {
            brain.say("Missing materials for " + targetItem + ".");
        }
    }

    private boolean craftOne(Brain brain, String target) {
        var p = brain.ai.player;
        return switch (target.toLowerCase()) {
            case "planks", "oak_planks" -> craftPlanks(brain, Items.OAK_LOG, Items.OAK_PLANKS);
            case "spruce_planks" -> craftPlanks(brain, Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
            case "birch_planks" -> craftPlanks(brain, Items.BIRCH_LOG, Items.BIRCH_PLANKS);
            case "sticks", "stick" -> craftSticks(brain);
            case "torch", "torches" -> craftTorches(brain);
            case "crafting_table" -> craftCraftingTable(brain);
            case "wooden_pickaxe" -> craftTool(brain, Items.WOODEN_PICKAXE, "planks");
            case "stone_pickaxe" -> craftTool(brain, Items.STONE_PICKAXE, "cobblestone");
            case "wooden_axe" -> craftTool(brain, Items.WOODEN_AXE, "planks");
            case "stone_axe" -> craftTool(brain, Items.STONE_AXE, "cobblestone");
            case "wooden_sword" -> craftTool(brain, Items.WOODEN_SWORD, "planks");
            case "stone_sword" -> craftTool(brain, Items.STONE_SWORD, "cobblestone");
            default -> {
                brain.say("I don't know how to craft '" + target + "' yet.");
                yield false;
            }
        };
    }

    private boolean craftPlanks(Brain brain, Item log, Item planks) {
        var p = brain.ai.player;
        if (!InventoryUtil.consumeItem(p, log, 1)) return false;
        p.getInventory().insertStack(new ItemStack(planks, 4));
        return true;
    }

    private boolean craftSticks(Brain brain) {
        var p = brain.ai.player;
        // Try oak planks first, then any planks
        if (InventoryUtil.consumeItem(p, Items.OAK_PLANKS, 2)) {
            p.getInventory().insertStack(new ItemStack(Items.STICK, 4));
            return true;
        }
        if (InventoryUtil.consumeItem(p, Items.SPRUCE_PLANKS, 2)) {
            p.getInventory().insertStack(new ItemStack(Items.STICK, 4));
            return true;
        }
        if (InventoryUtil.consumeItem(p, Items.BIRCH_PLANKS, 2)) {
            p.getInventory().insertStack(new ItemStack(Items.STICK, 4));
            return true;
        }
        return false;
    }

    private boolean craftTorches(Brain brain) {
        var p = brain.ai.player;
        if (!InventoryUtil.consumeItem(p, Items.COAL, 1) && !InventoryUtil.consumeItem(p, Items.CHARCOAL, 1)) return false;
        if (!InventoryUtil.consumeItem(p, Items.STICK, 1)) return false;
        p.getInventory().insertStack(new ItemStack(Items.TORCH, 4));
        return true;
    }

    private boolean craftCraftingTable(Brain brain) {
        var p = brain.ai.player;
        if (!InventoryUtil.consumeItem(p, Items.OAK_PLANKS, 4)) return false;
        p.getInventory().insertStack(new ItemStack(Items.CRAFTING_TABLE, 1));
        return true;
    }

    private boolean craftTool(Brain brain, Item tool, String material) {
        var p = brain.ai.player;
        // Ensure sticks
        if (InventoryUtil.countItem(p, Items.STICK) < 2) {
            if (!craftSticks(brain)) return false;
        }
        Item mat = material.equals("planks") ? Items.OAK_PLANKS : Items.COBBLESTONE;
        // Tools need 2-3 material + 2 sticks; simplified: consume 3 material + 2 sticks
        if (!InventoryUtil.consumeItem(p, mat, 3)) return false;
        if (!InventoryUtil.consumeItem(p, Items.STICK, 2)) return false;
        p.getInventory().insertStack(new ItemStack(tool, 1));
        return true;
    }

    @Override public boolean isComplete(Brain brain) {
        return crafted >= targetCount;
    }

    @Override public void stop(Brain brain) {
        brain.say("Crafted " + crafted + " " + targetItem + ".");
    }

    public CraftTask configure(String item, int count) {
        this.targetItem = item;
        this.targetCount = Math.max(1, count);
        return this;
    }
}
