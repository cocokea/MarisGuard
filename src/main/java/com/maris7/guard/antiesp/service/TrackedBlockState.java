package com.maris7.guard.antiesp.service;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public record TrackedBlockState(int x, int y, int z, BlockData realBlockData, BlockData maskedBlockData, boolean initiallyMasked) {

    public static TrackedBlockState create(int x, int y, int z, BlockData realBlockData, Material maskMaterial, boolean initiallyMasked) {
        return new TrackedBlockState(x, y, z, realBlockData, maskMaterial.createBlockData(), initiallyMasked);
    }

    public long blockKey() {
        return packBlockKey(x, y, z);
    }

    public static long packBlockKey(int x, int y, int z) {
        long lx = ((long) x & 0x3FFFFFFL) << 38;
        long lz = ((long) z & 0x3FFFFFFL) << 12;
        long ly = (long) y & 0xFFFL;
        return lx | lz | ly;
    }
}

