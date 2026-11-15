package com.aicompanion.player;

import com.aicompanion.AIMod;
import com.aicompanion.ai.Brain;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and manages fake-player AI companions.
 *
 * <p>Each AI is a real {@link ServerPlayerEntity} with a dummy network handler,
 * so it appears in the player list, has inventory/health/hunger, can be seen
 * by other players, and interacts with the world exactly like a human.
 *
 * <p>Supports multiple AIs (one per owner by default, configurable).
 */
public class FakePlayerManager {
    private final Map<UUID, AIPlayer> ais = new ConcurrentHashMap<>();
    private final Map<String, UUID> ownerToAi = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ summon

    /**
     * Summon an AI companion for {@code owner} (or standalone if owner is null).
     * Returns the AI player wrapper, or null if summon failed.
     */
    public AIPlayer summon(MinecraftServer server, ServerPlayerEntity owner, String name) {
        if (server == null) return null;
        String aiName = (name != null && !name.isBlank()) ? name : AIMod.CONFIG.aiName;

        // One AI per owner by default — dismiss old one first
        if (owner != null) {
            UUID existing = ownerToAi.get(owner.getUuidAsString());
            if (existing != null && ais.containsKey(existing)) {
                dismiss(server, existing);
            }
        }

        // Don't allow duplicate names
        for (AIPlayer p : ais.values()) {
            if (p.player.getName().getString().equalsIgnoreCase(aiName)) {
                if (owner != null) {
                    owner.sendMessage(Text.literal("An AI named '" + aiName + "' already exists.")
                            .formatted(Formatting.RED), false);
                }
                return p;
            }
        }

        try {
            ServerWorld world = owner != null ? owner.getServerWorld() : server.getWorld(net.minecraft.world.World.OVERWORLD);
            if (world == null) {
                // Fallback: first available world
                world = server.getWorlds().iterator().next();
            }

            GameProfile profile = new GameProfile(UUID.randomUUID(), aiName);

            // A dummy connection + network handler makes the entity behave like a
            // real connected player, without any actual network traffic.
            SyncedClientOptions options = SyncedClientOptions.createDefault();
            ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, options);
            ClientConnection dummyConnection = new ClientConnection(NetworkSide.SERVER);
            ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
            player.networkHandler = new ServerPlayNetworkHandler(server, dummyConnection, player, clientData);
            player.changeGameMode(GameMode.SURVIVAL);

            // Spawn near owner, or at world spawn
            if (owner != null) {
                player.setPos(owner.getX(), owner.getY(), owner.getZ());
                player.setYaw(owner.getYaw());
                player.setPitch(owner.getPitch());
            } else {
                var spawn = world.getSpawnPos();
                player.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            }
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0f);

            // Register with the player manager (tab list, stats, chunk sending)
            server.getPlayerManager().onPlayerConnect(dummyConnection, player, clientData);
            // Make sure the AI is actually spawned into its world
            if (world.getEntityById(player.getId()) == null) {
                world.spawnEntity(player);
            }

            AIPlayer ai = new AIPlayer(player, owner != null ? owner.getUuid() : null);
            ai.brain = new Brain(ai);
            ais.put(player.getUuid(), ai);
            if (owner != null) {
                ownerToAi.put(owner.getUuidAsString(), player.getUuid());
            }

            AIMod.LOGGER.info("[AI Companion] Summoned '{}' at {}", aiName, player.getBlockPos());
            broadcast(server, Text.literal(aiName + " joined the game").formatted(Formatting.AQUA));
            if (owner != null) {
                owner.sendMessage(Text.literal("Your AI companion " + aiName + " has joined! Use /ai help for commands.")
                        .formatted(Formatting.GREEN), false);
            }
            ai.brain.say("Hi! I'm " + aiName + ". I'll follow you and help out — just tell me what to do!");
            return ai;
        } catch (Exception e) {
            AIMod.LOGGER.error("[AI Companion] Failed to summon AI", e);
            if (owner != null) {
                owner.sendMessage(Text.literal("Failed to summon AI: " + e.getMessage()).formatted(Formatting.RED), false);
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ dismiss

    public boolean dismiss(MinecraftServer server, UUID aiUuid) {
        AIPlayer ai = ais.remove(aiUuid);
        if (ai == null) return false;
        try {
            ServerPlayerEntity p = ai.player;
            broadcast(server, Text.literal(p.getName().getString() + " left the game").formatted(Formatting.GRAY));
            try {
                if (p.networkHandler != null) {
                    p.networkHandler.disconnect(Text.literal("AI dismissed"));
                }
            } catch (Exception ignored) {
            }
            server.getPlayerManager().remove(p);
            try {
                p.getServerWorld().removePlayer(p, Entity.RemovalReason.DISCARDED);
            } catch (Exception ignored) {
            }
            // Clean owner mapping
            ownerToAi.values().removeIf(v -> v.equals(aiUuid));
            AIMod.LOGGER.info("[AI Companion] Dismissed '{}'", p.getName().getString());
            return true;
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Error dismissing AI", e);
            return false;
        }
    }

    public boolean dismissByOwner(ServerPlayerEntity owner) {
        UUID id = ownerToAi.get(owner.getUuidAsString());
        if (id == null) return false;
        return dismiss(owner.getServer(), id);
    }

    // ------------------------------------------------------------------ lookup

    public Collection<AIPlayer> all() {
        return Collections.unmodifiableCollection(ais.values());
    }

    public AIPlayer byUuid(UUID id) {
        return ais.get(id);
    }

    public AIPlayer forOwner(ServerPlayerEntity owner) {
        UUID id = ownerToAi.get(owner.getUuidAsString());
        return id != null ? ais.get(id) : null;
    }

    public AIPlayer byName(String name) {
        for (AIPlayer p : ais.values()) {
            if (p.player.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public int count() {
        return ais.size();
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftServer server) {
        // Clean up dead/removed AIs, respawn if needed
        List<UUID> toRemove = new ArrayList<>();
        for (AIPlayer ai : ais.values()) {
            ServerPlayerEntity p = ai.player;
            if (p.isRemoved() || !p.isAlive()) {
                // Respawn after death if owner still wants it
                if (ai.shouldRespawn && p.isRemoved()) {
                    AIMod.LOGGER.info("[AI Companion] '{}' died, respawning...", p.getName().getString());
                    // Re-summon at owner or spawn
                    ServerPlayerEntity owner = findOwner(server, ai.ownerUuid);
                    String name = p.getName().getString();
                    toRemove.add(p.getUuid());
                    summon(server, owner, name);
                } else if (p.isRemoved()) {
                    toRemove.add(p.getUuid());
                }
                continue;
            }
            // Keep hunger topped up a bit so the AI doesn't starve while idle
            // (EatTask handles real eating; this prevents unfair starvation)
            try {
                ai.brain.tick();
            } catch (Exception e) {
                AIMod.LOGGER.error("[AI Companion] Brain tick failed for {}", p.getName().getString(), e);
            }
        }
        for (UUID id : toRemove) {
            ais.remove(id);
        }
    }

    private ServerPlayerEntity findOwner(MinecraftServer server, UUID ownerUuid) {
        if (ownerUuid == null) return null;
        return server.getPlayerManager().getPlayer(ownerUuid);
    }

    public void saveAll() {
        for (AIPlayer ai : ais.values()) {
            try {
                ai.brain.saveMemory();
            } catch (Exception ignored) {}
        }
    }

    private void broadcast(MinecraftServer server, Text msg) {
        try {
            server.getPlayerManager().sendToAll(msg, false);
        } catch (Exception ignored) {}
    }
}
