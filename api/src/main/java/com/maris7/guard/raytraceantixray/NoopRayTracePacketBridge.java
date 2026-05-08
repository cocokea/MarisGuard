package com.maris7.guard.raytraceantixray;

import org.bukkit.entity.Player;

public final class NoopRayTracePacketBridge implements RayTracePacketBridge {
    @Override
    public boolean sendPacketImmediately(Player player, Object packet) {
        return false;
    }
}
