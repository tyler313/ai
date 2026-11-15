package com.aicompanion.build;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * A buildable structure: list of (offset, block) placed relative to an anchor.
 */
public record Blueprint(String name, List<PlacedBlock> blocks, int sizeX, int sizeZ) {

    public record PlacedBlock(BlockPos offset, Block block) {}

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<PlacedBlock> blocks = new java.util.ArrayList<>();
        private int maxX = 0, maxZ = 0;

        Builder(String name) { this.name = name; }

        public Builder add(int x, int y, int z, Block block) {
            blocks.add(new PlacedBlock(new BlockPos(x, y, z), block));
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            return this;
        }

        /** Fill a rectangular box (inclusive). */
        public Builder box(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        add(x, y, z, block);
                    }
                }
            }
            return this;
        }

        /** Hollow box walls (no floor/ceiling). */
        public Builder walls(int x1, int z1, int x2, int z2, int y1, int y2, Block block) {
            for (int y = y1; y <= y2; y++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    add(x, y, Math.min(z1, z2), block);
                    add(x, y, Math.max(z1, z2), block);
                }
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    add(Math.min(x1, x2), y, z, block);
                    add(Math.max(x1, x2), y, z, block);
                }
            }
            return this;
        }

        public Blueprint build() {
            return new Blueprint(name, List.copyOf(blocks), maxX + 1, maxZ + 1);
        }
    }
}
