package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.item.ItemStack;

/**
 * Eat food when hungry. Picks the most satiating food in inventory.
 */
public class EatTask implements Task {
    private long eatCooldown = 0;

    @Override public String name() { return "Eat"; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.autoEat) return 0;
        int hunger = brain.ai.player.getHungerManager().getFoodLevel();
        if (hunger > 14) return 0;
        if (!InventoryUtil.hasFood(brain.ai.player)) return 0;
        // More urgent when very hungry
        return hunger < 6 ? 85 : 50;
    }

    @Override public boolean isUrgent(Brain brain) {
        return brain.ai.player.getHungerManager().getFoodLevel() < 4 && InventoryUtil.hasFood(brain.ai.player);
    }

    @Override public void tick(Brain brain) {
        long now = System.currentTimeMillis();
        if (now < eatCooldown) return;
        eatCooldown = now + 1000;

        var p = brain.ai.player;
        if (p.getHungerManager().getFoodLevel() >= 20) return;

        ItemStack food = InventoryUtil.findBestFood(p);
        if (food.isEmpty()) return;

        // Eat it (server-side: consume directly)
        try {
            // Use the food item's use logic via interaction manager if available,
            // otherwise consume manually.
            p.getHungerManager().eat(food.getItem(), food);
            food.decrement(1);
        } catch (Exception e) {
            // Fallback: manual hunger restore
            p.getHungerManager().setFoodLevel(Math.min(20, p.getHungerManager().getFoodLevel() + 4));
            food.decrement(1);
        }
    }

    @Override public boolean isComplete(Brain brain) {
        return brain.ai.player.getHungerManager().getFoodLevel() >= 18
                || !InventoryUtil.hasFood(brain.ai.player);
    }
}
