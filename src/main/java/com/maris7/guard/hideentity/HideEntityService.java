package com.maris7.guard.hideentity;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HideEntityService {
    private static final double MIN_SCAN_DISTANCE = 48.0D;
    private static final double EXTRA_SCAN_PADDING = 16.0D;

    private final MarisGuard plugin;
    private final File file;
    private YamlConfiguration config;
    private Set<EntityType> hiddenTypes = EnumSet.noneOf(EntityType.class);
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
        hiddenEntityIds.clear();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        this.hiddenTypes = loadHiddenTypes();
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
        double scanDistance = Math.max(MIN_SCAN_DISTANCE, distance + EXTRA_SCAN_PADDING);
        double maxDistanceSquared = distance * distance;
        Set<UUID> hidden = hiddenEntityIds.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        Set<UUID> scanned = new HashSet<>();

        for (Entity nearby : viewer.getNearbyEntities(scanDistance, scanDistance, scanDistance)) {
            if (shouldIgnore(viewer, nearby)) {
                continue;
            }
            scanned.add(nearby.getUniqueId());
            if (isWithinDistance(viewer, nearby, maxDistanceSquared)) {
                if (hidden.remove(nearby.getUniqueId())) {
                    viewer.showEntity(plugin, nearby);
                }
            } else if (!hidden.contains(nearby.getUniqueId())) {
                viewer.hideEntity(plugin, nearby);
                hidden.add(nearby.getUniqueId());
            }
        }

        Iterator<UUID> iterator = hidden.iterator();
        while (iterator.hasNext()) {
            UUID entityId = iterator.next();
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                iterator.remove();
                continue;
            }
            if (entity.getWorld() != viewer.getWorld()) {
                viewer.showEntity(plugin, entity);
                iterator.remove();
                continue;
            }
            if (scanned.contains(entityId) && isWithinDistance(viewer, entity, maxDistanceSquared)) {
                viewer.showEntity(plugin, entity);
                iterator.remove();
            }
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

    private long getRefreshTicks() {
        return Math.max(1L, config.getLong("refresh-ticks", 20L));
    }

    private boolean shouldIgnore(Player viewer, Entity entity) {
        return entity == null
                || entity == viewer
                || entity instanceof Player
                || !entity.isValid()
                || entity.getWorld() != viewer.getWorld()
                || !hiddenTypes.contains(entity.getType());
    }

    private boolean isWithinDistance(Player viewer, Entity entity, double maxDistanceSquared) {
        return viewer.getLocation().distanceSquared(entity.getLocation()) <= maxDistanceSquared;
    }

    private Set<EntityType> loadHiddenTypes() {
        List<String> configured = config.getStringList("entity-types");
        if (configured.isEmpty()) {
            return EnumSet.complementOf(EnumSet.of(EntityType.PLAYER));
        }
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String value : configured) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
                if (type != EntityType.PLAYER) {
                    types.add(type);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return types;
    }
}
