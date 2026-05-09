package com.maris7.guard.playertrace;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerVisibilityRaytraceService implements Listener {

    private static final Set<String> DEFAULT_WORLDS = Set.of("world");
    private static final double[] NEAR_SAMPLE_HEIGHTS = {0.25D, 0.95D, 1.62D};
    private static final double[] FAR_SAMPLE_HEIGHTS = {0.95D};
    private static final double NEAR_DISTANCE_SQUARED = 400.0D;

    private final MarisGuard plugin;
    private final PlayerVisibilityPacketBridge packetBridge;
    private final ConcurrentMap<UUID, ViewerState> viewerStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Player> entityIdToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> foliaTasks = new ConcurrentHashMap<>();
    private PlayerVisibilityPacketListener packetListener;
    private BukkitTask bukkitTask;
    private boolean enabled;
    private boolean folia;
    private Set<String> worlds = DEFAULT_WORLDS;
    private double maxDistance;
    private double maxDistanceSquared;
    private int periodTicks;
    private int maxTargetsPerTick;
    private int candidateRefreshTicks;

    public PlayerVisibilityRaytraceService(MarisGuard plugin) {
        this.plugin = plugin;
        this.packetBridge = plugin.getPlayerVisibilityPacketBridge();
    }

    public void start() {
        reload();
        if (!enabled) {
            return;
        }
        this.folia = isFoliaRuntime();
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(this, plugin);
        this.packetListener = new PlayerVisibilityPacketListener(this);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        for (Player player : Bukkit.getOnlinePlayers()) {
            entityIdToPlayer.put(player.getEntityId(), player);
        }
        if (folia) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduleFolia(player);
            }
        } else {
            this.bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllBukkit, periodTicks, periodTicks);
        }
        plugin.getLogger().info("[PlayerRaytrace] Enabled. worlds=" + worlds + ", period=" + periodTicks + "t, maxTargetsPerTick=" + maxTargetsPerTick);
    }

    public void stop() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
        if (bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        }
        for (ScheduledTask task : foliaTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        foliaTasks.clear();
        for (UUID viewerId : new HashSet<>(viewerStates.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                revealAll(viewer);
            }
        }
        viewerStates.clear();
        entityIdToPlayer.clear();
    }

    public void reload() {
        FileConfiguration config = plugin.getPlayerRaytraceConfig();
        this.enabled = config.getBoolean("player-visibility-raytrace.enabled", true);
        this.maxDistance = Math.max(8.0D, config.getDouble("player-visibility-raytrace.max-distance", 48.0D));
        this.maxDistanceSquared = maxDistance * maxDistance;
        this.periodTicks = Math.max(20, config.getInt("player-visibility-raytrace.check-period-ticks", 10));
        this.maxTargetsPerTick = Math.min(2, Math.max(1, config.getInt("player-visibility-raytrace.max-targets-per-tick", 5)));
        this.candidateRefreshTicks = Math.max(10, config.getInt("player-visibility-raytrace.candidate-refresh-ticks", 5));

        List<String> configuredWorlds = config.getStringList("player-visibility-raytrace.worlds");
        Set<String> normalized = new HashSet<>();
        for (String world : configuredWorlds) {
            if (world != null && !world.isBlank()) {
                normalized.add(world.toLowerCase(Locale.ROOT));
            }
        }
        this.worlds = normalized.isEmpty() ? DEFAULT_WORLDS : Collections.unmodifiableSet(normalized);
    }

    public boolean isHiddenFrom(UUID viewerId, int entityId) {
        ViewerState state = viewerStates.get(viewerId);
        return state != null && state.hiddenEntityIds.contains(entityId);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        entityIdToPlayer.put(event.getPlayer().getEntityId(), event.getPlayer());
        if (enabled && folia) {
            scheduleFolia(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        entityIdToPlayer.remove(event.getPlayer().getEntityId());
        UUID viewerId = event.getPlayer().getUniqueId();
        ScheduledTask task = foliaTasks.remove(viewerId);
        if (task != null) {
            task.cancel();
        }
        viewerStates.remove(viewerId);
        removeTargetFromAll(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        revealAll(event.getPlayer());
        if (enabled && folia) {
            scheduleFolia(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getFrom().getWorld() != event.getTo().getWorld()) {
            revealAll(event.getPlayer());
        }
    }

    private void tickAllBukkit() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickViewer(player);
        }
    }

    private void scheduleFolia(Player player) {
        ScheduledTask oldTask = foliaTasks.remove(player.getUniqueId());
        if (oldTask != null) {
            oldTask.cancel();
        }
        if (!player.isOnline() || !player.isValid()) {
            return;
        }
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
            if (!player.isOnline() || !player.isValid()) {
                scheduledTask.cancel();
                foliaTasks.remove(player.getUniqueId());
                return;
            }
            tickViewer(player);
        }, null, periodTicks, periodTicks);
        if (task != null) {
            foliaTasks.put(player.getUniqueId(), task);
        }
    }

    private void tickViewer(Player viewer) {
        if (!viewer.isOnline() || !viewer.isValid() || !isActiveWorld(viewer.getWorld()) || viewer.getGameMode() == GameMode.SPECTATOR) {
            revealAll(viewer);
            return;
        }

        Location viewerLocation = viewer.getLocation();
        double vx = viewerLocation.getX();
        double vy = viewerLocation.getY();
        double vz = viewerLocation.getZ();

        ViewerState state = viewerStates.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerState());
        hideSuppressedNearby(viewer, state);
        List<Player> candidates;
        if (--state.candidateRefreshCountdown <= 0) {
            candidates = findCandidates(viewer, vx, vy, vz);
            state.cachedCandidates = candidates;
            state.candidateRefreshCountdown = candidateRefreshTicks;
        } else {
            candidates = sanitizeCachedCandidates(viewer, state.cachedCandidates, vx, vy, vz);
            state.cachedCandidates = candidates;
        }

        revealOutOfRange(viewer, state, candidates);
        if (candidates.isEmpty()) {
            return;
        }

        int checked = 0;
        int start = state.cursor % candidates.size();
        while (checked < maxTargetsPerTick && checked < candidates.size()) {
            Player target = candidates.get((start + checked) % candidates.size());
            boolean visible = hasAnyVisibleRay(viewer, target);
            setVisible(viewer, target, visible, state);
            checked++;
        }
        state.cursor = (start + checked) % candidates.size();
    }


    private List<Player> findCandidates(Player viewer, double vx, double vy, double vz) {
        List<Player> candidates = new ArrayList<>();
        for (org.bukkit.entity.Entity nearby : viewer.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (nearby instanceof Player target && isCandidate(viewer, target, vx, vy, vz)) {
                candidates.add(target);
            }
        }
        return candidates;
    }

    private void hideSuppressedNearby(Player viewer, ViewerState state) {
        for (org.bukkit.entity.Entity nearby : viewer.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (nearby instanceof Player target && shouldNeverReveal(viewer, target)) {
                hide(viewer, target, state);
            }
        }
    }

    private List<Player> sanitizeCachedCandidates(Player viewer, List<Player> cached, double vx, double vy, double vz) {
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }
        List<Player> candidates = new ArrayList<>(cached.size());
        for (Player target : cached) {
            if (isCandidate(viewer, target, vx, vy, vz)) {
                candidates.add(target);
            }
        }
        return candidates;
    }

    private boolean isCandidate(Player viewer, Player target, double vx, double vy, double vz) {
        if (shouldNeverReveal(viewer, target)
                || target == viewer
                || !target.isOnline()
                || !target.isValid()
                || target.getGameMode() == GameMode.SPECTATOR
                || !isActiveWorld(target.getWorld())) {
            return false;
        }
        if (target.getWorld() != viewer.getWorld()) {
            return false;
        }
        Location targetLocation = target.getLocation();
        double dx = targetLocation.getX() - vx;
        double dy = targetLocation.getY() - vy;
        double dz = targetLocation.getZ() - vz;
        return (dx * dx) + (dy * dy) + (dz * dz) <= maxDistanceSquared;
    }

    private void revealOutOfRange(Player viewer, ViewerState state, List<Player> candidates) {
        Set<Integer> nearby = new HashSet<>();
        for (Player candidate : candidates) {
            nearby.add(candidate.getEntityId());
        }
        for (Integer entityId : new HashSet<>(state.hiddenEntityIds)) {
            if (!nearby.contains(entityId)) {
                Player target = findPlayerByEntityId(entityId);
                if (target != null) {
                    if (shouldNeverReveal(viewer, target)) {
                        continue;
                    }
                    reveal(viewer, target, state);
                } else {
                    state.hiddenEntityIds.remove(entityId);
                }
            }
        }
    }

    private boolean hasAnyVisibleRay(Player viewer, Player target) {
        Location eye = viewer.getEyeLocation();
        World world = eye.getWorld();
        if (world == null || world != target.getWorld()) {
            return true;
        }

        Location base = target.getLocation();
        double ex = eye.getX();
        double ey = eye.getY();
        double ez = eye.getZ();
        double tx = base.getX();
        double ty = base.getY();
        double tz = base.getZ();

        double baseDx = tx - ex;
        double baseDy = ty - ey;
        double baseDz = tz - ez;
        double baseDistanceSquared = (baseDx * baseDx) + (baseDy * baseDy) + (baseDz * baseDz);
        double[] sampleHeights = baseDistanceSquared < NEAR_DISTANCE_SQUARED ? NEAR_SAMPLE_HEIGHTS : FAR_SAMPLE_HEIGHTS;

        for (double offset : sampleHeights) {
            double dx = tx - ex;
            double dy = (ty + offset) - ey;
            double dz = tz - ez;
            double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
            double distance = Math.sqrt(distanceSquared);
            if (distance <= 0.01D || distance > maxDistance) {
                return true;
            }

            Vector direction = new Vector(dx / distance, dy / distance, dz / distance);
            RayTraceResult result = world.rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
            if (result == null) {
                return true;
            }

            Vector hit = result.getHitPosition();
            double hitDx = hit.getX() - ex;
            double hitDy = hit.getY() - ey;
            double hitDz = hit.getZ() - ez;
            if ((hitDx * hitDx) + (hitDy * hitDy) + (hitDz * hitDz) + 0.0144D >= distanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void setVisible(Player viewer, Player target, boolean visible, ViewerState state) {
        if (shouldNeverReveal(viewer, target)) {
            hide(viewer, target, state);
        } else if (visible) {
            reveal(viewer, target, state);
        } else {
            hide(viewer, target, state);
        }
    }

    private boolean shouldNeverReveal(Player viewer, Player target) {
        return target == null
                || viewer == null
                || !viewer.isOnline()
                || !target.isOnline()
                || !target.isValid()
                || target.isInvisible()
                || !viewer.canSee(target);
    }

    private void hide(Player viewer, Player target, ViewerState state) {
        int entityId = target.getEntityId();
        if (!state.hiddenEntityIds.add(entityId)) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    private void reveal(Player viewer, Player target, ViewerState state) {
        int entityId = target.getEntityId();
        if (!state.hiddenEntityIds.remove(entityId)) {
            return;
        }
        if (!viewer.isOnline() || !target.isOnline() || viewer.getWorld() != target.getWorld()) {
            return;
        }
        packetBridge.sendSpawnPackets(viewer, target);
    }

    private void revealAll(Player viewer) {
        ViewerState state = viewerStates.get(viewer.getUniqueId());
        if (state == null || state.hiddenEntityIds.isEmpty()) {
            return;
        }
        for (Integer entityId : new HashSet<>(state.hiddenEntityIds)) {
            Player target = findPlayerByEntityId(entityId);
            if (target != null) {
                reveal(viewer, target, state);
            } else {
                state.hiddenEntityIds.remove(entityId);
            }
        }
        if (state.hiddenEntityIds.isEmpty()) {
            viewerStates.remove(viewer.getUniqueId(), state);
        }
    }

    private Player findPlayerByEntityId(int entityId) {
        return entityIdToPlayer.get(entityId);
    }

    private void removeTargetFromAll(int entityId) {
        for (ViewerState state : viewerStates.values()) {
            state.hiddenEntityIds.remove(entityId);
        }
    }

    private boolean isActiveWorld(World world) {
        return world != null
                && WorldNameMatcher.contains(worlds, world)
                && (plugin.getRevealService() == null || !plugin.getRevealService().isWorldBlacklisted(world));
    }

    private boolean isFoliaRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static final class ViewerState {
        private final Set<Integer> hiddenEntityIds = ConcurrentHashMap.newKeySet();
        private List<Player> cachedCandidates = Collections.emptyList();
        private int candidateRefreshCountdown;
        private int cursor;
    }
}
