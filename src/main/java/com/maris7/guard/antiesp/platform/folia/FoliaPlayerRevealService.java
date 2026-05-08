package com.maris7.guard.antiesp.platform.folia;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.service.AbstractPlayerRevealService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FoliaPlayerRevealService extends AbstractPlayerRevealService {

    private final Map<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();

    public FoliaPlayerRevealService(MarisGuard plugin) {
        super(plugin);
    }

    @Override
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateKnownWorld(player);
            cachePlayerLocation(player);
            schedulePlayerTask(player);
        }
    }

    @Override
    public void stop() {
        for (ScheduledTask task : playerTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        playerTasks.clear();
    }

    @Override
    protected void handlePlayerJoin(Player player) {
        schedulePlayerTask(player);
    }

    @Override
    protected void handlePlayerQuit(Player player) {
        final ScheduledTask task = playerTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onChangedWorldReschedule(PlayerChangedWorldEvent event) {
        schedulePlayerTask(event.getPlayer());
    }

    private void schedulePlayerTask(Player player) {
        final ScheduledTask oldTask = playerTasks.remove(player.getUniqueId());
        if (oldTask != null) {
            oldTask.cancel();
        }

        if (!player.isOnline() || !player.isValid()) {
            return;
        }

        final int period = getRefreshPeriodTicks();
        final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline() || !player.isValid()) {
                scheduledTask.cancel();
                playerTasks.remove(player.getUniqueId());
                return;
            }
            updateVisibility(player);
        }, null, period, period);

        if (task != null) {
            playerTasks.put(player.getUniqueId(), task);
        }
    }
}

