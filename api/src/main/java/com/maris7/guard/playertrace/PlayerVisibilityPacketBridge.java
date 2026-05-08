package com.maris7.guard.playertrace;

import org.bukkit.entity.Player;

public interface PlayerVisibilityPacketBridge {
    void sendSpawnPackets(Player viewer, Player target);
}
