package com.maris7.guard.raytraceantixray;

import org.bukkit.entity.Player;

public interface RayTracePacketBridge {
    boolean sendPacketImmediately(Player player, Object packet);
}
