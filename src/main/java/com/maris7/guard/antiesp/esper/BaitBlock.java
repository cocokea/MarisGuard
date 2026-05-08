package com.maris7.guard.antiesp.esper;

import org.bukkit.block.data.BlockData;

public record BaitBlock(int x, int y, int z, BlockData realBlockData, BlockData fakeBlockData, long blockKey) {
    public static long packKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    public static BaitBlock create(int x, int y, int z, BlockData realBlockData, BlockData fakeBlockData) {
        return new BaitBlock(x, y, z, realBlockData, fakeBlockData, packKey(x, y, z));
    }
}

