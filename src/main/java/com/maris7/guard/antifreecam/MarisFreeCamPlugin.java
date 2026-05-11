package com.maris7.guard.antifreecam;

import com.github.retrooper.packetevents.PacketEvents;
import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarisFreeCamPlugin implements Listener {
    private static final int NO_HIDE = Integer.MIN_VALUE;

    private final MarisGuard plugin;
    private final ChunkPacketListenerPE packetListener;
    private final Map<UUID, Integer> activeHideBelow = new ConcurrentHashMap<>();
    private final Queue<ChunkRefresh> refreshQueue = new ArrayDeque<>();
    private final Set<String> queuedRefreshes = ConcurrentHashMap.newKeySet();
    private ScheduledTask foliaTask;
    private int bukkitTaskId = -1;

    public MarisFreeCamPlugin(MarisGuard plugin) {
        this.plugin = plugin;
        this.packetListener = new ChunkPacketListenerPE(this);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        startQueuePump();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEnabledFor(player)) {
                activeHideBelow.remove(player.getUniqueId());
                continue;
            }
            activeHideBelow.put(player.getUniqueId(), resolveHideBelowY(player.getLocation().getY()));
        }
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        if (foliaTask != null) {
            foliaTask.cancel();
            foliaTask = null;
        }
        if (bukkitTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bukkitTaskId);
            bukkitTaskId = -1;
        }
        refreshQueue.clear();
        queuedRefreshes.clear();
        activeHideBelow.clear();
    }

    public boolean isEnabledFor(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.getAntiFreeCamConfig().getBoolean("antifreecam.enabled", true)) {
            return false;
        }
        List<String> whitelistWorlds = plugin.getAntiFreeCamConfig().getStringList("antifreecam.whitelist-worlds");
        if (!whitelistWorlds.isEmpty() && !WorldNameMatcher.contains(whitelistWorlds, player.getWorld())) {
            return false;
        }
        return !WorldNameMatcher.contains(plugin.getAntiEspConfig().getStringList("blacklist-worlds"), player.getWorld());
    }

    public int getHideBelowY(Player player) {
        if (!isEnabledFor(player)) {
            return NO_HIDE;
        }
        return activeHideBelow.computeIfAbsent(player.getUniqueId(), ignored -> resolveHideBelowY(player.getLocation().getY()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        updateBand(event.getPlayer(), event.getPlayer().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        activeHideBelow.remove(uniqueId);
        clearQueuedRefreshes(uniqueId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getWorld() == to.getWorld() && from.getBlockY() == to.getBlockY()) {
            return;
        }
        updateBand(event.getPlayer(), to, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        updateBand(event.getPlayer(), event.getTo(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateBand(event.getPlayer(), event.getPlayer().getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        updateBand(event.getPlayer(), event.getRespawnLocation(), true);
    }

    private void updateBand(Player player, Location to, boolean forceRefresh) {
        if (to == null || !player.isOnline()) {
            return;
        }
        int previous = activeHideBelow.getOrDefault(player.getUniqueId(), NO_HIDE);
        int next = isEnabledFor(player) ? resolveHideBelowY(to.getY()) : NO_HIDE;
        if (!forceRefresh && previous == next) {
            return;
        }

        activeHideBelow.put(player.getUniqueId(), next);
        if (!isEnabledFor(player)) {
            clearQueuedRefreshes(player.getUniqueId());
            return;
        }
        if (previous != next) {
            clearQueuedRefreshes(player.getUniqueId());
        }
        queueNearbyRefreshes(player, next, to);
    }

    private void clearQueuedRefreshes(UUID playerId) {
        synchronized (refreshQueue) {
            refreshQueue.removeIf(refresh -> refresh.playerId.equals(playerId));
        }
        queuedRefreshes.removeIf(key -> key.startsWith(playerId.toString() + "|"));
    }

    private void queueNearbyRefreshes(Player player, int nextHideBelow, Location to) {
        World world = to.getWorld();
        if (world == null) {
            return;
        }

        int radius = Math.max(1, plugin.getAntiFreeCamConfig().getInt("antifreecam.refresh-radius-chunks", 2));
        int centerChunkX = to.getBlockX() >> 4;
        int centerChunkZ = to.getBlockZ() >> 4;
        List<ChunkRefresh> refreshes = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                refreshes.add(new ChunkRefresh(
                        player.getUniqueId(),
                        world.getKey().toString(),
                        chunkX,
                        chunkZ,
                        nextHideBelow,
                        Math.abs(dx) + Math.abs(dz)
                ));
            }
        }

        refreshes.sort(Comparator.comparingInt(ChunkRefresh::priority));
        synchronized (refreshQueue) {
            for (ChunkRefresh refresh : refreshes) {
                String key = refresh.queueKey();
                if (queuedRefreshes.add(key)) {
                    refreshQueue.add(refresh);
                }
            }
        }
    }

    private void startQueuePump() {
        Runnable runner = this::drainRefreshQueue;
        if (plugin.isFolia()) {
            foliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runner.run(), 1L, 1L);
        } else {
            bukkitTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, runner, 1L, 1L);
        }
    }

    private void drainRefreshQueue() {
        int budget = Math.max(1, plugin.getAntiFreeCamConfig().getInt("antifreecam.refresh-chunks-per-tick", 4));
        for (int i = 0; i < budget; i++) {
            ChunkRefresh refresh;
            synchronized (refreshQueue) {
                refresh = refreshQueue.poll();
            }
            if (refresh == null) {
                return;
            }
            queuedRefreshes.remove(refresh.queueKey());
            applyRefresh(refresh);
        }
    }

    private void applyRefresh(ChunkRefresh refresh) {
        Player player = Bukkit.getPlayer(refresh.playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(refresh.worldKey));
        if (world == null || player.getWorld() != world) {
            return;
        }
        int currentHideBelow = getHideBelowY(player);
        if (currentHideBelow != refresh.hideBelowY) {
            return;
        }

        if (plugin.isFolia()) {
            Bukkit.getRegionScheduler().execute(plugin, world, refresh.chunkX, refresh.chunkZ, () -> {
                if (world.isChunkLoaded(refresh.chunkX, refresh.chunkZ)) {
                    world.refreshChunk(refresh.chunkX, refresh.chunkZ);
                }
            });
        } else if (world.isChunkLoaded(refresh.chunkX, refresh.chunkZ)) {
            world.refreshChunk(refresh.chunkX, refresh.chunkZ);
        }
    }

    private int resolveHideBelowY(double playerY) {
        if (playerY > 40.0D) {
            return 30;
        }
        if (playerY > 10.0D) {
            return 0;
        }
        if (playerY > -10.0D) {
            return -20;
        }
        if (playerY > -30.0D) {
            return -40;
        }
        return NO_HIDE;
    }

    private record ChunkRefresh(UUID playerId, String worldKey, int chunkX, int chunkZ, int hideBelowY, int priority) {
        private String queueKey() {
            return playerId + "|" + worldKey.toLowerCase(Locale.ROOT) + "|" + chunkX + "|" + chunkZ + "|" + hideBelowY;
        }
    }
}
