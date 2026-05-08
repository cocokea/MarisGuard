package com.maris7.guard.raytraceantixray.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.raytraceantixray.data.PlayerData;
import com.maris7.guard.raytraceantixray.data.VectorialLocation;
import com.maris7.guard.raytraceantixray.tasks.RayTraceCallable;

public final class PlayerListener implements Listener {
    private final MarisGuard plugin;

    public PlayerListener(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.validatePlayer(player)) {
            return;
        }
        if (!plugin.isEnabled(player.getWorld())) {
            plugin.removePlayerData(player);
            return;
        }

        PlayerData playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(player.getEyeLocation())));
        playerData.setCallable(new RayTraceCallable(plugin, playerData));
        plugin.putPlayerData(player, playerData);

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removePlayerData(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (!plugin.isEnabled(to.getWorld())) {
            plugin.removePlayerData(player);
            return;
        }

        PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());
        if (playerData == null) {
            playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(to)));
            playerData.setCallable(new RayTraceCallable(plugin, playerData));
            plugin.putPlayerData(player, playerData);
        }

        if (!plugin.validatePlayerData(player, playerData, "onPlayerMove")) {
            return;
        }

        if (to.getWorld().equals(playerData.getLocations()[0].getWorld())) {
            VectorialLocation location = new VectorialLocation(to);
            Vector vector = location.getVector();
            vector.setY(vector.getY() + player.getEyeHeight());
            playerData.setLocations(MarisGuard.getLocations(player, location));
        }
    }
}

