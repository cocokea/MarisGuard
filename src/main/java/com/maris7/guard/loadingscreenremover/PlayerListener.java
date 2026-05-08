package com.maris7.guard.loadingscreenremover;

import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.maris7.guard.MarisGuard;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

@AllArgsConstructor
public class PlayerListener extends SimplePacketListenerAbstract implements Listener {
    private final MarisGuard plugin;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerManager().updateLastKnownWorld(event.getPlayer());
        plugin.debug("Initial world for " + event.getPlayer().getName() + ": " + describeWorld(event.getPlayer().getWorld()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleportWorld(PlayerTeleportEvent event) {
        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();
        if (fromWorld == null || toWorld == null) {
            plugin.debug("Teleport ignored for " + event.getPlayer().getName() + ": missing world");
            return;
        }

        plugin.getPlayerManager().updateLastKnownWorld(event.getPlayer(), fromWorld);

        if (fromWorld == toWorld) {
            plugin.debug("Teleport ignored for " + event.getPlayer().getName() + ": same world " + fromWorld.getName());
            return;
        }

        if (fromWorld.getEnvironment() != toWorld.getEnvironment()) {
            plugin.debug("Teleport ignored for " + event.getPlayer().getName() + ": different environments "
                    + describeWorld(fromWorld) + " -> " + describeWorld(toWorld));
            return;
        }

        track(event.getPlayer(), "teleport event " + describeWorld(fromWorld) + " -> " + describeWorld(toWorld));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();
        boolean deathRespawn = plugin.getPlayerManager().hasRecentlyDiedPlayer(player);
        boolean sameEnvironment = fromWorld != null
                && toWorld != null
                && fromWorld.getEnvironment() == toWorld.getEnvironment();

        plugin.getPlayerManager().updateLastKnownWorld(player, fromWorld);

        if (sameEnvironment && !deathRespawn) {
            plugin.getPlayerManager().addChangingWorldPlayer(player);
            plugin.debug("Tracking " + player.getName() + " for changed-world event "
                    + describeWorld(fromWorld) + " -> " + describeWorld(toWorld)
                    + " for " + plugin.getTrackTicks() + " ticks");
        } else {
            plugin.debug("PlayerChangedWorldEvent not tracked for " + player.getName()
                    + ": deathRespawn=" + deathRespawn
                    + ", sameEnvironment=" + sameEnvironment
                    + ", from=" + describeWorld(fromWorld)
                    + ", to=" + describeWorld(toWorld));
        }

        plugin.getTaskScheduler().runTaskLater(
                () -> {
                    plugin.getPlayerManager().removeChangingWorldPlayer(player);
                    if (player.isOnline()) {
                        plugin.getPlayerManager().updateLastKnownWorld(player);
                        plugin.debug("Changed-world cleanup for " + player.getName()
                                + ": lastKnown=" + describeWorld(player.getWorld()));
                    }
                },
                plugin.getTrackTicks()
        );
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getPlayerManager().addRecentlyDiedPlayer(player);
        plugin.debug("Death respawn bypass armed for " + player.getName() + " for " + plugin.getDeathBypassTicks() + " ticks");
        plugin.getTaskScheduler().runTaskLater(
                () -> {
                    plugin.getPlayerManager().removeRecentlyDiedPlayer(player);
                    plugin.debug("Expired death respawn bypass for " + player.getName());
                },
                plugin.getDeathBypassTicks()
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerManager().clearPlayer(event.getPlayer());
        plugin.debug("Removed tracking for quit player " + event.getPlayer().getName());
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.RESPAWN) {
            return;
        }

        Player player = event.getPlayer();
        WrapperPlayServerRespawn respawn = new WrapperPlayServerRespawn(event);
        Optional<String> packetWorldName = respawn.getWorldName();
        World packetWorld = packetWorldName.map(this::findWorld).orElse(null);
        World targetWorld = packetWorld != null ? packetWorld : player.getWorld();
        Optional<PlayerManager.WorldSnapshot> previousWorld = plugin.getPlayerManager().getLastKnownWorld(player);

        boolean recentlyDied = plugin.getPlayerManager().consumeRecentlyDiedPlayer(player);
        boolean deathRespawn = recentlyDied;
        boolean tracked = !deathRespawn && plugin.getPlayerManager().isPlayerChangingWorlds(player);
        boolean aggressive = !deathRespawn
                && plugin.isAggressiveSameEnvironment()
                && isSameEnvironmentRespawn(previousWorld.orElse(null), targetWorld);
        plugin.debug("RESPAWN packet for " + player.getName()
                + ": deathRespawn=" + deathRespawn
                + ", tracked=" + tracked
                + ", aggressive=" + aggressive
                + ", previous=" + previousWorld.map(Object::toString).orElse("unknown")
                + ", current=" + describeWorld(player.getWorld())
                + ", packetWorld=" + packetWorldName.orElse("unknown")
                + (packetWorld == null ? "(unmapped, target=" + describeWorld(targetWorld) + ")" : " -> " + describeWorld(packetWorld)));

        if (deathRespawn) {
            plugin.debug("Allowed RESPAWN packet for " + player.getName()
                    + " because it belongs to a death respawn");
        } else if (tracked || aggressive) {
            event.setCancelled(true);
            plugin.debug("Cancelled RESPAWN packet for " + player.getName()
                    + " by " + (tracked ? "tracked teleport" : "same-environment fallback"));
        }

        if (packetWorld != null) {
            plugin.getPlayerManager().updateLastKnownWorld(player, packetWorld);
            plugin.debug("Updated lastKnown for " + player.getName() + " to packet world " + describeWorld(packetWorld));
        } else {
            plugin.getPlayerManager().updateLastKnownWorld(player);
            plugin.debug("Updated lastKnown for " + player.getName() + " to current world " + describeWorld(player.getWorld()));
        }
    }

    private void track(Player player, String reason) {
        plugin.getPlayerManager().addChangingWorldPlayer(player);
        plugin.debug("Tracking " + player.getName() + " for " + reason + " for " + plugin.getTrackTicks() + " ticks");
        plugin.getTaskScheduler().runTaskLater(
                () -> {
                    plugin.getPlayerManager().removeChangingWorldPlayer(player);
                    plugin.debug("Expired tracking for " + player.getName());
                },
                plugin.getTrackTicks()
        );
    }

    private boolean isSameEnvironmentRespawn(PlayerManager.WorldSnapshot previousWorld, World packetWorld) {
        return previousWorld != null
                && packetWorld != null
                && previousWorld.getEnvironment() == packetWorld.getEnvironment();
    }

    private World findWorld(String worldName) {
        World byName = Bukkit.getWorld(worldName);
        if (byName != null) {
            return byName;
        }

        NamespacedKey key = NamespacedKey.fromString(worldName);
        return key == null ? null : Bukkit.getWorld(key);
    }

    private String describeWorld(World world) {
        return world == null ? "unknown" : world.getName() + "(" + world.getEnvironment() + ", key=" + world.getKey() + ")";
    }
}
