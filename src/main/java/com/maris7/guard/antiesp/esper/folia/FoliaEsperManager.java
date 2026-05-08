package com.maris7.guard.antiesp.esper.folia;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class FoliaEsperManager extends AbstractEsperManager {

    private final Map<UUID, ScheduledTask> tickerTasks = new ConcurrentHashMap<>();
    private ScheduledTask autoCheckTask;

    public FoliaEsperManager(MarisGuard plugin) {
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
        for (ScheduledTask task : tickerTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        tickerTasks.clear();
    }

    @Override
    protected void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    @Override
    protected void runForPlayerLater(Player player, long delayTicks, Runnable task) {
        player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1L, delayTicks));
    }

    @Override
    protected void runGlobalRepeating(long delayTicks, long periodTicks, Runnable task) {
        cancelAutoCheckTask();
        autoCheckTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
    }

    @Override
    protected void runGlobalLater(long delayTicks, Runnable task) {
        if (delayTicks <= 0L) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return;
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }

    @Override
    protected void cancelAutoCheckTask() {
        if (autoCheckTask != null) {
            autoCheckTask.cancel();
            autoCheckTask = null;
        }
    }

    @Override
    protected void scheduleSessionTicker(Player player) {
        cancelSessionTicker(player.getUniqueId());
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline() || !player.isValid()) {
                scheduledTask.cancel();
                tickerTasks.remove(player.getUniqueId());
                return;
            }
            tickSession(player);
        }, null, 1L, 5L);

        if (task != null) {
            tickerTasks.put(player.getUniqueId(), task);
        } else {
            clearSession(player.getUniqueId());
        }
    }

    @Override
    protected void cancelSessionTicker(UUID playerId) {
        ScheduledTask task = tickerTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}

