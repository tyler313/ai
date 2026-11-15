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

        // Eat it (server-side: apply the food component directly)
        var foodComponent = food.get(net.minecraft.component.DataComponentTypes.FOOD);
        if (foodComponent != null) {
            p.getHungerManager().eat(foodComponent);
        } else {
            p.getHungerManager().setFoodLevel(Math.min(20, p.getHungerManager().getFoodLevel() + 4));
        }
        p.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        food.decrement(1);
    }

    @Override public boolean isComplete(Brain brain) {
        return brain.ai.player.getHungerManager().getFoodLevel() >= 18
                || !InventoryUtil.hasFood(brain.ai.player);
    }
}
