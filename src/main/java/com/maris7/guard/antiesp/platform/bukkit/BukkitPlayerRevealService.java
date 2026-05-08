package com.maris7.guard.antiesp.platform.bukkit;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.service.AbstractPlayerRevealService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BukkitPlayerRevealService extends AbstractPlayerRevealService {

    private int taskId = -1;

    public BukkitPlayerRevealService(MarisGuard plugin) {
        super(plugin);
    }

    @Override
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateKnownWorld(player);
            cachePlayerLocation(player);
        }
        final int period = getRefreshPeriodTicks();
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickPlayers, period, period);
    }

    @Override
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tickPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || !player.isValid()) {
                continue;
            }
            updateVisibility(player);
        }
    }
}

