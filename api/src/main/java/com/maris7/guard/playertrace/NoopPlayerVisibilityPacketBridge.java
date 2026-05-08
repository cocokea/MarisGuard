package com.maris7.guard.playertrace;

import org.bukkit.entity.Player;

public final class NoopPlayerVisibilityPacketBridge implements PlayerVisibilityPacketBridge {
    @Override
    public void sendSpawnPackets(Player viewer, Player target) {
        // No-op fallback when no version-specific bridge is available.
    }
}
