package com.maris7.guard.antiseedcracker;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public final class AntiSeedCrackerPacketListener extends PacketListenerAbstract {
    private final MarisGuard plugin;

    public AntiSeedCrackerPacketListener(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }

        FileConfiguration config = plugin.getAntiSeedCrackerConfig();
        if (config == null || !config.getBoolean("antiseedcracker.enabled", true)) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            handleJoinGame(event, config);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            handleRespawn(event, config);
        }
    }

    private void handleJoinGame(PacketSendEvent event, FileConfiguration config) {
        if (!config.getBoolean("antiseedcracker.spoof-join-seed", true)) {
            return;
        }

        WrapperPlayServerJoinGame wrapper = new WrapperPlayServerJoinGame(event);
        if (!isWorldAllowed(config, wrapper.getWorldName())) {
            return;
        }

        wrapper.setHashedSeed(config.getLong("antiseedcracker.fake-seed", 0L));
        wrapper.write();
        event.markForReEncode(true);
    }

    private void handleRespawn(PacketSendEvent event, FileConfiguration config) {
        if (!config.getBoolean("antiseedcracker.spoof-respawn-seed", true)) {
            return;
        }

        WrapperPlayServerRespawn wrapper = new WrapperPlayServerRespawn(event);
        Optional<String> worldName = wrapper.getWorldName();
        if (worldName.isPresent() && !isWorldAllowed(config, worldName.get())) {
            return;
        }

        wrapper.setHashedSeed(config.getLong("antiseedcracker.fake-seed", 0L));
        wrapper.write();
        event.markForReEncode(true);
    }

    private boolean isWorldAllowed(FileConfiguration config, String packetWorldName) {
        List<String> worlds = config.getStringList("antiseedcracker.worlds");
        if (worlds.isEmpty() || packetWorldName == null || packetWorldName.isBlank()) {
            return true;
        }
        return WorldNameMatcher.containsKey(worlds, packetWorldName);
    }
}
