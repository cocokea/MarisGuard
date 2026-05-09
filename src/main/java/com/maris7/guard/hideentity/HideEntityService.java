package com.maris7.guard.hideentity;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HideEntityService {
    private final MarisGuard plugin;
    private final File file;
    private YamlConfiguration config;
    private final Map<UUID, Set<UUID>> hiddenEntityIds = new ConcurrentHashMap<>();
    private ScheduledTask foliaTask;
    private int bukkitTaskId = -1;

    public HideEntityService(MarisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "modules/hideEntity.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        reload();
        Runnable runner = this::tick;
        if (plugin.isFolia()) {
            foliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runner.run(), 20L, getRefreshTicks());
        } else {
            bukkitTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, runner, 20L, getRefreshTicks());
        }
    }

    public void stop() {
        if (foliaTask != null) {
            foliaTask.cancel();
            foliaTask = null;
        }
        if (bukkitTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bukkitTaskId);
            bukkitTaskId = -1;
        }
        restoreAll();
        hiddenEntityIds.clear();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void tick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            runForViewer(viewer, () -> processViewer(viewer));
        }
    }

    private void processViewer(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        if (!config.getBoolean("enabled", true)) {
            restore(viewer);
            return;
        }
        if (!isEnabledFor(viewer)) {
            restore(viewer);
            return;
        }
        update(viewer);
    }

    private void runForViewer(Player viewer, Runnable runnable) {
        if (plugin.isFolia()) {
            viewer.getScheduler().run(plugin, ignored -> runnable.run(), null);
            return;
        }
        runnable.run();
    }

    private boolean isEnabledFor(Player viewer) {
        List<String> worlds = config.getStringList("worlds");
        return worlds.isEmpty() || WorldNameMatcher.contains(worlds, viewer.getWorld());
    }

    private void update(Player viewer) {
        double distance = Math.max(1.0D, config.getDouble("distance", 20.0D));
        Set<UUID> hidden = hiddenEntityIds.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        Set<UUID> stillHidden = new HashSet<>();

        for (Entity nearby : viewer.getNearbyEntities(distance, distance, distance)) {
            if (nearby instanceof Player || nearby == viewer) {
                continue;
            }
            stillHidden.add(nearby.getUniqueId());
        }

        for (Entity entity : viewer.getWorld().getEntities()) {
            if (entity instanceof Player || entity == viewer || !entity.isValid()) {
                continue;
            }
            if (stillHidden.contains(entity.getUniqueId())) {
                if (!viewer.canSee(entity)) {
                    viewer.showEntity(plugin, entity);
                }
                hidden.remove(entity.getUniqueId());
                continue;
            }
            if (viewer.canSee(entity)) {
                viewer.hideEntity(plugin, entity);
            }
            hidden.add(entity.getUniqueId());
        }

        if (hidden.isEmpty()) {
            hiddenEntityIds.remove(viewer.getUniqueId());
        }
    }

    private void restore(Player viewer) {
        Set<UUID> hidden = hiddenEntityIds.remove(viewer.getUniqueId());
        if (hidden == null) {
            return;
        }
        for (UUID entityId : hidden) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                viewer.showEntity(plugin, entity);
            }
        }
    }

    private void restoreAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            runForViewer(viewer, () -> restore(viewer));
        }
    }

    private long getRefreshTicks() {
        return Math.max(1L, config.getLong("refresh-ticks", 20L));
    }
}
