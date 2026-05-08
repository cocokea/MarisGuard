package com.maris7.guard.raytraceantixray.data;

import java.util.UUID;

public final class ChunkPacketKey {
    private final UUID worldId;
    private final long chunkKey;

    public ChunkPacketKey(UUID worldId, long chunkKey) {
        this.worldId = worldId;
        this.chunkKey = chunkKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ChunkPacketKey)) {
            return false;
        }

        ChunkPacketKey other = (ChunkPacketKey) obj;
        return chunkKey == other.chunkKey && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        int result = worldId.hashCode();
        result = 31 * result + Long.hashCode(chunkKey);
        return result;
    }
}

