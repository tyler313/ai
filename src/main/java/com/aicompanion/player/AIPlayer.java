package com.aicompanion.player;

import com.aicompanion.ai.Brain;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Wrapper around a fake {@link ServerPlayerEntity} + its {@link Brain}.
 */
public class AIPlayer {
    public final ServerPlayerEntity player;
    public final UUID ownerUuid; // nullable — standalone AI
    public Brain brain;
    public boolean shouldRespawn = true;

    // Movement state (used by Pathfinder / MovementController)
    public java.util.List<BlockPos> currentPath = new java.util.ArrayList<>();
    public int pathIndex = 0;
    public long lastPathTime = 0;
    public BlockPos stuckPos = null;
    public int stuckTicks = 0;
    public long lastMoveTime = 0;

    public AIPlayer(ServerPlayerEntity player, UUID ownerUuid) {
        this.player = player;
        this.ownerUuid = ownerUuid;
        this.lastMoveTime = System.currentTimeMillis();
    }

    public String name() {
        return player.getName().getString();
    }

    public BlockPos pos() {
        return player.getBlockPos();
    }

    public boolean isAlive() {
        return player.isAlive() && !player.isRemoved();
    }
}
