package com.aicompanion.ai.tasks;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.aicompanion.ai.Task;
import com.aicompanion.util.InventoryUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fight hostiles (or a specified target). Uses melee with strafing,
 * bow if available, and retreats when health is low.
 */
public class CombatTask implements Task {
    public LivingEntity forcedTarget = null;
    public boolean fightAnimals = false;

    private long lastAttack = 0;
    private long strafeFlip = 0;
    private int strafeDir = 1;
    private long lastBowShot = 0;

    @Override public String name() { return "Combat"; }

    @Override public double score(Brain brain) {
        if (!AIMod.CONFIG.allowCombat) return 0;
        var p = brain.ai.player;
        // Don't fight if nearly dead (Flee handles that)
        if (p.getHealth() < p.getMaxHealth() * 0.2f) return 0;

        if (forcedTarget != null && forcedTarget.isAlive() && !forcedTarget.isRemoved()) return 80;

        LivingEntity hostile = brain.perception.nearestHostile();
        if (hostile != null) {
            double d = p.distanceTo(hostile);
            if (d < 8) return 75;
            if (d < 20) return 45;
        }
        if (fightAnimals) {
            LivingEntity animal = brain.perception.nearestAnimal();
            if (animal != null && p.distanceTo(animal) < 12) return 40;
        }
        return 0;
    }

    @Override public boolean isUrgent(Brain brain) {
        LivingEntity h = brain.perception.nearestHostile();
        return h != null && brain.ai.player.distanceTo(h) < 4 && score(brain) > 0;
    }

    @Override public void start(Brain brain) {
        if (forcedTarget != null) {
            brain.say("Attacking " + forcedTarget.getName().getString() + "!");
        }
    }

    @Override public void tick(Brain brain) {
        var p = brain.ai.player;
        LivingEntity target = resolveTarget(brain);
        if (target == null) return;

        double dist = p.distanceTo(target);
        brain.movement.lookAt(target.getPos());

        // Equip best weapon
        InventoryUtil.equipBestWeapon(p);

        // Ranged: use bow if we have one and target is far
        boolean hasBow = InventoryUtil.hasItem(p, Items.BOW);
        boolean hasArrows = InventoryUtil.hasItem(p, Items.ARROW);
        if (hasBow && hasArrows && dist > 6 && dist < 32) {
            long now = System.currentTimeMillis();
            if (now - lastBowShot > 1500) {
                lastBowShot = now;
                shootBow(brain, target);
            }
            // Keep distance while shooting
            if (dist < 8) {
                Vec3d away = p.getPos().subtract(target.getPos()).normalize().multiply(3);
                brain.movement.setDestination(p.getBlockPos().add((int) away.x, 0, (int) away.z), 1.0);
            } else {
                brain.movement.stop();
            }
            return;
        }

        // Melee
        if (dist > 2.5) {
            brain.movement.followEntity(target, 1.4, 2.0);
        } else {
            brain.movement.stop();
            // Strafe slightly for crits / dodging
            long now = System.currentTimeMillis();
            if (now - strafeFlip > 1200) {
                strafeFlip = now;
                strafeDir *= -1;
            }
            // Attack with cooldown
            if (now - lastAttack > 500) {
                lastAttack = now;
                try {
                    p.attack(target);
                    p.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    if (target instanceof LivingEntity le && le.getHealth() <= 0) {
                        brain.memory.kills++;
                    }
                } catch (Exception e) {
                    AIMod.LOGGER.warn("[AI Companion] Attack failed", e);
                }
            }
        }
    }

    private void shootBow(Brain brain, LivingEntity target) {
        var p = brain.ai.player;
        // Find bow + arrow slots
        int bowSlot = InventoryUtil.findItemSlot(p, Items.BOW);
        if (bowSlot < 0) return;
        try {
            // Equip bow to main hand temporarily
            ItemStack main = p.getMainHandStack();
            ItemStack bow = p.getInventory().getStack(bowSlot);
            p.getInventory().setStack(bowSlot, main);
            p.getInventory().setStack(p.getInventory().selectedSlot, bow);
            // Spawn an arrow travelling toward the target (as if we loosed a bow).
            Vec3d from = p.getEyePos();
            Vec3d to = target.getPos().add(0, target.getHeight() * 0.8, 0);
            Vec3d dir = to.subtract(from).normalize();
            var arrow = new net.minecraft.entity.projectile.ArrowEntity(
                    p.getServerWorld(), p, new ItemStack(Items.ARROW), p.getMainHandStack());
            arrow.refreshPositionAndAngles(from.x, from.y, from.z, p.getYaw(), p.getPitch());
            arrow.setVelocity(dir.x, dir.y, dir.z, 1.9f, 1.0f);
            p.getServerWorld().spawnEntity(arrow);
            // Consume arrow
            InventoryUtil.consumeItem(p, Items.ARROW, 1);
            // Restore main hand
            p.getInventory().setStack(p.getInventory().selectedSlot, main);
            p.getInventory().setStack(bowSlot, bow);
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Bow shot failed", e);
        }
    }

    private LivingEntity resolveTarget(Brain brain) {
        if (forcedTarget != null && forcedTarget.isAlive() && !forcedTarget.isRemoved()) {
            return forcedTarget;
        }
        forcedTarget = null;
        LivingEntity h = brain.perception.nearestHostile();
        if (h != null) return h;
        if (fightAnimals) return brain.perception.nearestAnimal();
        return null;
    }

    @Override public boolean isComplete(Brain brain) {
        return resolveTarget(brain) == null;
    }

    @Override public void stop(Brain brain) {
        forcedTarget = null;
        fightAnimals = false;
    }

    /** Attack a specific entity. */
    public CombatTask attack(LivingEntity target) {
        this.forcedTarget = target;
        return this;
    }

    public CombatTask huntAnimals(boolean hunt) {
        this.fightAnimals = hunt;
        return this;
    }
}
