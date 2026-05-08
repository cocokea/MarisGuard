package com.maris7.guard.antiesp.esper.bukkit;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BukkitEsperManager extends AbstractEsperManager {

    private final Map<UUID, Integer> tickerTasks = new ConcurrentHashMap<>();
    private Integer autoCheckTaskId;

    public BukkitEsperManager(MarisGuard plugin) {
        super(plugin);
    }

    @Override
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            CompletableFuture.supplyAsync(() -> violationStorage.loadViolations(player.getUniqueId()))
                    .thenAccept(vl -> runForPlayer(player, () -> {
                        if (player.isOnline()) {
                            violations.put(player.getUniqueId(), vl);
                        }
                    }));
        }
        startAutoCheckScheduler();
    }

    @Override
    public void stop() {
        cancelAutoCheckTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            violationStorage.saveViolationsAsync(player.getUniqueId(), player.getName(), violations.getOrDefault(player.getUniqueId(), 0));
        }
        for (Integer taskId : tickerTasks.values()) {
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
        }
        tickerTasks.clear();
    }

    @Override
    protected void runForPlayer(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    protected void runForPlayerLater(Player player, long delayTicks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    @Override
    protected void runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
        cancelAutoCheckTask();
        autoCheckTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
    }

    @Override
    protected void runGlobalLater(long delayTicks, Runnable task) {
        if (delayTicks <= 0L) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    protected void cancelAutoCheckTask() {
        if (autoCheckTaskId != null) {
            Bukkit.getScheduler().cancelTask(autoCheckTaskId);
            autoCheckTaskId = null;
        }
    }

    @Override
    protected void scheduleSessionTicker(Player player) {
        cancelSessionTicker(player.getUniqueId());
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline() || !player.isValid()) {
                cancelSessionTicker(player.getUniqueId());
                return;
            }
            tickSession(player);
        }, 1L, 5L);
        tickerTasks.put(player.getUniqueId(), taskId);
    }

    @Override
    protected void cancelSessionTicker(UUID playerId) {
        Integer taskId = tickerTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}

