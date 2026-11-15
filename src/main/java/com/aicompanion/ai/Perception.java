package com.aicompanion.ai;

import com.aicompanion.AIMod;
import com.aicompanion.player.AIPlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * What the AI can see right now: nearby blocks, entities, dangers, and the owner.
 * Scanned every brain tick (throttled) and cached for task scoring.
 */
public class Perception {
    public final AIPlayer ai;
    public final Memory memory;

    // Cached results
    public List<LivingEntity> nearbyHostiles = new ArrayList<>();
    public List<LivingEntity> nearbyAnimals = new ArrayList<>();
    public List<Entity> nearbyDrops = new ArrayList<>();
    public Map<String, List<BlockPos>> nearbyResources = new HashMap<>();
    public ServerPlayerEntity owner = null;
    public boolean ownerNearby = false;
    public double ownerDistance = Double.MAX_VALUE;
    public boolean isNight = false;
    public boolean isRaining = false;
    public int dangerLevel = 0; // 0 safe, 1 cautious, 2 danger, 3 critical
    public BlockPos nearestLava = null;
    public BlockPos nearestFire = null;
    public BlockPos nearestBed = null;
    public BlockPos nearestCraftingTable = null;
    public BlockPos nearestFurnace = null;
    public BlockPos nearestChest = null;
    public long lastScanTime = 0;

    private static final Set<Block> ORE_BLOCKS = Set.of(
            Blocks.DIAMOND_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.COAL_ORE,
            Blocks.REDSTONE_ORE, Blocks.LAPIS_LAZULI_ORE, Blocks.EMERALD_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_LAZULI_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS
    );

    private static final Set<Block> LOG_BLOCKS = Set.of(
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG
    );

    public Perception(AIPlayer ai, Memory memory) {
        this.ai = ai;
        this.memory = memory;
    }

    public void scan() {
        long now = System.currentTimeMillis();
        if (now - lastScanTime < 500) return; // throttle to 2Hz
        lastScanTime = now;

        ServerPlayerEntity p = ai.player;
        ServerWorld world = p.getServerWorld();
        BlockPos center = p.getBlockPos();
        int radius = AIMod.CONFIG.perceptionRadius;

        // --- Entities ---
        nearbyHostiles.clear();
        nearbyAnimals.clear();
        nearbyDrops.clear();
        double r2 = radius * radius;
        var scanBox = new net.minecraft.util.math.Box(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius);
        for (Entity e : world.getOtherEntities(null, scanBox)) {
            if (e == p) continue;
            double d2 = e.squaredDistanceTo(p);
            if (d2 > r2) continue;
            if (e instanceof HostileEntity h) {
                nearbyHostiles.add(h);
            } else if (e instanceof PassiveEntity a) {
                nearbyAnimals.add(a);
            } else if (e instanceof net.minecraft.entity.ItemEntity) {
                nearbyDrops.add(e);
            }
        }
        nearbyHostiles.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        nearbyAnimals.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));
        nearbyDrops.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(p)));

        // --- Owner ---
        owner = null;
        ownerNearby = false;
        ownerDistance = Double.MAX_VALUE;
        if (ai.ownerUuid != null) {
            owner = p.getServer().getPlayerManager().getPlayer(ai.ownerUuid);
            if (owner != null && owner.getServerWorld() == world) {
                ownerDistance = p.distanceTo(owner);
                ownerNearby = ownerDistance <= radius;
                memory.lastKnownPlayerPos = owner.getBlockPos().toImmutable();
            }
        }

        // --- Time / weather ---
        isNight = world.isNight();
        isRaining = world.isRaining();

        // --- Blocks (spiral scan, capped for perf) ---
        nearbyResources.clear();
        nearestLava = null;
        nearestFire = null;
        nearestBed = null;
        nearestCraftingTable = null;
        nearestFurnace = null;
        nearestChest = null;

        int scanned = 0;
        int maxScan = 4096;
        for (int dx = -radius; dx <= radius && scanned < maxScan; dx++) {
            for (int dy = -8; dy <= 8 && scanned < maxScan; dy++) {
                for (int dz = -radius; dz <= radius && scanned < maxScan; dz++) {
                    if (dx * dx + dz * dz > r2) continue;
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    scanned++;

                    if (ORE_BLOCKS.contains(block)) {
                        addResource(blockName(block), pos);
                        memory.rememberResource(blockName(block), pos);
                    } else if (LOG_BLOCKS.contains(block)) {
                        addResource("log", pos);
                    } else if (block == Blocks.LAVA) {
                        if (nearestLava == null || pos.getSquaredDistance(center) < nearestLava.getSquaredDistance(center)) {
                            nearestLava = pos.toImmutable();
                        }
                    } else if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
                        if (nearestFire == null || pos.getSquaredDistance(center) < nearestFire.getSquaredDistance(center)) {
                            nearestFire = pos.toImmutable();
                        }
                    } else if (block == Blocks.RED_BED || block == Blocks.WHITE_BED || block == Blocks.BLACK_BED
                            || block == Blocks.BLUE_BED || block == Blocks.BROWN_BED || block == Blocks.CYAN_BED
                            || block == Blocks.GRAY_BED || block == Blocks.GREEN_BED || block == Blocks.LIGHT_BLUE_BED
                            || block == Blocks.LIGHT_GRAY_BED || block == Blocks.LIME_BED || block == Blocks.MAGENTA_BED
                            || block == Blocks.ORANGE_BED || block == Blocks.PINK_BED || block == Blocks.PURPLE_BED
                            || block == Blocks.YELLOW_BED) {
                        if (nearestBed == null) nearestBed = pos.toImmutable();
                    } else if (block == Blocks.CRAFTING_TABLE) {
                        if (nearestCraftingTable == null) nearestCraftingTable = pos.toImmutable();
                    } else if (block == Blocks.FURNACE) {
                        if (nearestFurnace == null) nearestFurnace = pos.toImmutable();
                    } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                        if (nearestChest == null) nearestChest = pos.toImmutable();
                    } else if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES
                            || block == Blocks.BEETROOTS || block == Blocks.MELON || block == Blocks.PUMPKIN) {
                        addResource("crop", pos);
                    } else if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.STONE
                            || block == Blocks.COBBLESTONE || block == Blocks.SAND) {
                        // Don't spam memory with common blocks
                    }
                }
            }
        }

        // --- Danger level ---
        dangerLevel = 0;
        if (!nearbyHostiles.isEmpty()) {
            double nearest = p.distanceTo(nearbyHostiles.get(0));
            if (nearest < 6) dangerLevel = Math.max(dangerLevel, 3);
            else if (nearest < 12) dangerLevel = Math.max(dangerLevel, 2);
            else dangerLevel = Math.max(dangerLevel, 1);
        }
        if (nearestLava != null && p.getBlockPos().getSquaredDistance(nearestLava) < 16) {
            dangerLevel = Math.max(dangerLevel, 2);
        }
        if (nearestFire != null && p.getBlockPos().getSquaredDistance(nearestFire) < 9) {
            dangerLevel = Math.max(dangerLevel, 2);
        }
        if (p.getHealth() < p.getMaxHealth() * 0.3f) {
            dangerLevel = Math.max(dangerLevel, 2);
        }
        if (p.getHealth() < p.getMaxHealth() * 0.15f) {
            dangerLevel = 3;
        }
    }

    private void addResource(String name, BlockPos pos) {
        nearbyResources.computeIfAbsent(name, k -> new ArrayList<>()).add(pos.toImmutable());
    }

    public List<BlockPos> getResources(String name) {
        List<BlockPos> list = nearbyResources.get(name);
        if (list != null && !list.isEmpty()) return list;
        return memory.getKnown(name);
    }

    public boolean hasResource(String name) {
        return !getResources(name).isEmpty();
    }

    public LivingEntity nearestHostile() {
        return nearbyHostiles.isEmpty() ? null : nearbyHostiles.get(0);
    }

    public LivingEntity nearestAnimal() {
        return nearbyAnimals.isEmpty() ? null : nearbyAnimals.get(0);
    }

    public Entity nearestDrop() {
        return nearbyDrops.isEmpty() ? null : nearbyDrops.get(0);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("pos=").append(ai.pos())
          .append(" hp=").append(String.format("%.0f/%.0f", ai.player.getHealth(), ai.player.getMaxHealth()))
          .append(" hunger=").append(ai.player.getHungerManager().getFoodLevel())
          .append(" night=").append(isNight)
          .append(" danger=").append(dangerLevel)
          .append(" hostiles=").append(nearbyHostiles.size())
          .append(" ownerDist=").append(ownerDistance == Double.MAX_VALUE ? "none" : String.format("%.0f", ownerDistance));
        for (Map.Entry<String, List<BlockPos>> e : nearbyResources.entrySet()) {
            sb.append(" ").append(e.getKey()).append("=").append(e.getValue().size());
        }
        return sb.toString();
    }

    public static String blockName(Block b) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(b).getPath();
        return id; // e.g. "diamond_ore"
    }
}
