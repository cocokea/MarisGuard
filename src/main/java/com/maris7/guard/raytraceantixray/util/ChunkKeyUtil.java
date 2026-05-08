package com.maris7.guard.raytraceantixray.util;

public final class ChunkKeyUtil {
    private ChunkKeyUtil() {
    }

    public static long asLong(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }
}
