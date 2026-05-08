package com.maris7.guard.raytraceantixray.data;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkBlocks {
    private final Reference<LevelChunk> chunk;
    private final LongWrapper key;
    private final Map<BlockPos, Boolean> blocks;

    public ChunkBlocks(LevelChunk chunk, Map<BlockPos, Boolean> blocks) {
        this.chunk = new WeakReference<>(chunk);
        ChunkPos pos = chunk.getPos();
        int chunkX = pos.getMinBlockX() >> 4;
        int chunkZ = pos.getMinBlockZ() >> 4;
        key = new LongWrapper(chunkKey(chunkX, chunkZ));
        this.blocks = blocks;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    public LevelChunk getChunk() {
        return chunk.get();
    }

    public LongWrapper getKey() {
        return key;
    }

    public Map<BlockPos, Boolean> getBlocks() {
        return blocks;
    }
}

