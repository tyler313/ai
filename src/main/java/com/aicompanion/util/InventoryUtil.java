package com.aicompanion.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Inventory helpers: counting, finding, equipping best tools/weapons, eating.
 */
public class InventoryUtil {

    public static int countItem(ServerPlayerEntity p, Item item) {
        int total = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    public static boolean hasItem(ServerPlayerEntity p, Item item) {
        return countItem(p, item) > 0;
    }

    public static int findItemSlot(ServerPlayerEntity p, Item item) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    public static int findBlockSlot(ServerPlayerEntity p, Block block) {
        Item item = block.asItem();
        return findItemSlot(p, item);
    }

    /** Consume {@code count} of {@code item} from inventory. Returns true on success. */
    public static boolean consumeItem(ServerPlayerEntity p, Item item, int count) {
        if (countItem(p, item) < count) return false;
        int remaining = count;
        for (int i = 0; i < p.getInventory().size() && remaining > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) {
                int take = Math.min(s.getCount(), remaining);
                s.decrement(take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    public static boolean isFull(ServerPlayerEntity p) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) return false;
        }
        return true;
    }

    public static boolean hasFood(ServerPlayerEntity p) {
        return findBestFood(p) != null && !findBestFood(p).isEmpty();
    }

    public static ItemStack findBestFood(ServerPlayerEntity p) {
        ItemStack best = null;
        int bestScore = -1;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            int score = foodScore(s.getItem());
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static int foodScore(Item item) {
        // Rough hunger values (higher = better)
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.GOLDEN_CARROT) return 10;
        if (item == Items.BREAD || item == Items.COOKED_CHICKEN || item == Items.COOKED_MUTTON || item == Items.COOKED_SALMON) return 8;
        if (item == Items.BAKED_POTATO || item == Items.COOKED_COD || item == Items.COOKED_RABBIT) return 7;
        if (item == Items.APPLE || item == Items.CARROT || item == Items.POTATO || item == Items.MELON_SLICE || item == Items.SWEET_BERRIES) return 5;
        if (item == Items.BEETROOT || item == Items.COOKIE || item == Items.PUMPKIN_PIE) return 4;
        if (item == Items.BREAD) return 6;
        if (item == Items.RAW_BEEF || item == Items.RAW_PORKCHOP || item == Items.RAW_CHICKEN || item == Items.RAW_MUTTON) return 2;
        if (item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE) return 1;
        return -1;
    }

    /** Equip the best pickaxe/axe/shovel for {@code state} to the selected slot. */
    public static void equipBestTool(ServerPlayerEntity p, BlockState state) {
        // Determine tool type from block
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        boolean needsPick = blockId.contains("ore") || blockId.contains("stone") || blockId.contains("deepslate")
                || blockId.contains("cobble") || blockId.contains("andesite") || blockId.contains("diorite")
                || blockId.contains("granite") || blockId.contains("tuff") || blockId.contains("sandstone")
                || blockId.contains("obsidian") || blockId.contains("netherrack") || blockId.contains("basalt")
                || blockId.contains("end_stone") || blockId.contains("brick") || blockId.contains("concrete");
        boolean needsAxe = blockId.contains("log") || blockId.contains("planks") || blockId.contains("wood");
        boolean needsShovel = blockId.contains("dirt") || blockId.contains("sand") || blockId.contains("gravel")
                || blockId.contains("clay") || blockId.contains("snow") || blockId.contains("soul_sand");

        Item[] picks = {Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE};
        Item[] axes = {Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE};
        Item[] shovels = {Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL, Items.STONE_SHOVEL, Items.WOODEN_SHOVEL};

        Item[] candidates = needsPick ? picks : needsAxe ? axes : needsShovel ? shovels : picks;
        for (Item tool : candidates) {
            int slot = findItemSlot(p, tool);
            if (slot >= 0 && slot < 9) {
                p.getInventory().selectedSlot = slot;
                return;
            }
        }
        // Fallback: any tool in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            String path = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
            if (path.contains("pickaxe") || path.contains("axe") || path.contains("shovel")) {
                p.getInventory().selectedSlot = i;
                return;
            }
        }
    }

    /** Equip the best sword/axe to the selected slot. */
    public static void equipBestWeapon(ServerPlayerEntity p) {
        Item[] weapons = {Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD,
                Items.WOODEN_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE};
        for (Item w : weapons) {
            int slot = findItemSlot(p, w);
            if (slot >= 0 && slot < 9) {
                p.getInventory().selectedSlot = slot;
                return;
            }
        }
    }
}
