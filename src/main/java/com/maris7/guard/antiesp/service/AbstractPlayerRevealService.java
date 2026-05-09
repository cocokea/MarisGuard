package com.maris7.guard.antiesp.service;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.util.WorldNameMatcher;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public abstract class AbstractPlayerRevealService implements Listener {


    protected final MarisGuard plugin;
    protected final int revealRadius;
    protected final int revealRadiusSquared;
    protected final Material maskMaterial;
    private final Set<Material> sensitiveBlocks;
    private final Set<String> sensitiveSuffixes;
    private final boolean maskBlockEntityPackets;
    private final List<String> blacklistedWorlds;

    private final Map<UUID, Map<Long, Map<Long, TrackedBlockState>>> trackedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> revealedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> bypassOutgoingBlockChangeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Long>> timedBypassOutgoingBlockChangeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownWorldByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> trackedWorldByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, CachedPlayerLocation> cachedLocationsByPlayer = new ConcurrentHashMap<>();

    protected AbstractPlayerRevealService(MarisGuard plugin) {
        this.plugin = plugin;
        this.revealRadius = Math.max(1, plugin.getConfig().getInt("reveal-radius", 16));
        this.revealRadiusSquared = revealRadius * revealRadius;
        this.maskMaterial = Material.matchMaterial(plugin.getConfig().getString("mask-material", "AIR"));
        if (this.maskMaterial == null || (!this.maskMaterial.isBlock() && !this.maskMaterial.isAir())) {
            throw new IllegalStateException("mask-material must be a valid block material or AIR");
        }
        this.sensitiveBlocks = loadSensitiveBlocks();
        this.sensitiveSuffixes = loadSensitiveSuffixes();
        this.maskBlockEntityPackets = plugin.getConfig().getBoolean("antiesp.mask-block-entity-packets", true);
        this.blacklistedWorlds = List.copyOf(plugin.getConfig().getStringList("blacklist-worlds"));
    }

    public abstract void start();
    public abstract void stop();

    public Material getMaskMaterial() {
        return maskMaterial;
    }

    public int getRevealRadiusSquared() {
        return revealRadiusSquared;
    }

    public int getRefreshPeriodTicks() {
        return Math.max(1, plugin.getConfig().getInt("refresh-period-ticks", 10));
    }

    public boolean isWorldBlacklisted(World world) {
        return world != null && WorldNameMatcher.contains(blacklistedWorlds, world);
    }

    public boolean isWorldBlacklisted(String worldName) {
        if (worldName == null) {
            return false;
        }
        return WorldNameMatcher.containsKey(blacklistedWorlds, worldName);
    }

    public boolean isPlayerInBlacklistedWorld(Player player) {
        if (player == null) {
            return false;
        }
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            World currentWorld = player.getWorld();
            if (currentWorld != null) {
                return isWorldBlacklisted(currentWorld);
            }
        }
        return isWorldBlacklisted(knownWorldByPlayer.get(player.getUniqueId()));
    }

    public boolean shouldMaskBlockEntityPackets() {
        return maskBlockEntityPackets;
    }

    public boolean isSensitive(Material material) {
        if (material == null) {
            return false;
        }
        if (sensitiveBlocks.contains(material)) {
            return true;
        }
        String name = material.name();
        for (String suffix : sensitiveSuffixes) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    protected final void updateKnownWorld(Player player) {
        World world = player.getWorld();
        if (world != null) {
            knownWorldByPlayer.put(player.getUniqueId(), world.getKey().toString());
        }
    }

    protected final void cachePlayerLocation(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        cachedLocationsByPlayer.put(player.getUniqueId(), new CachedPlayerLocation(
                location.getX(),
                location.getY(),
                location.getZ(),
                world == null ? null : world.getKey().toString()
        ));
    }

    public CachedPlayerLocation getCachedLocation(UUID playerId) {
        return cachedLocationsByPlayer.get(playerId);
    }

    public void markBypassOutgoingBlockChange(Player player, int x, int y, int z) {
        UUID uuid = player.getUniqueId();
        Set<Long> bypass = bypassOutgoingBlockChangeByPlayer.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        bypass.add(TrackedBlockState.packBlockKey(x, y, z));
    }

    public void markTimedBypassOutgoingBlockChange(Player player, int x, int y, int z, long ticks) {
        long expiresAt = System.currentTimeMillis() + Math.max(1L, ticks) * 50L;
        Map<Long, Long> timed = timedBypassOutgoingBlockChangeByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        timed.put(TrackedBlockState.packBlockKey(x, y, z), expiresAt);
    }

    public boolean consumeBypassOutgoingBlockChange(Player player, int x, int y, int z) {
        long blockKey = TrackedBlockState.packBlockKey(x, y, z);
        Set<Long> bypass = bypassOutgoingBlockChangeByPlayer.get(player.getUniqueId());
        if (bypass != null && bypass.remove(blockKey)) {
            return true;
        }

        Map<Long, Long> timed = timedBypassOutgoingBlockChangeByPlayer.get(player.getUniqueId());
        if (timed == null) {
            return false;
        }

        Long expiresAt = timed.get(blockKey);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            timed.remove(blockKey, expiresAt);
            return false;
        }
        return true;
    }

    public void upsertTrackedBlock(Player player, TrackedBlockState state) {
        if (isPlayerInBlacklistedWorld(player)) {
            clearMaskedState(player);
            return;
        }
        rememberTrackedWorld(player);
        UUID uuid = player.getUniqueId();
        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        long chunkKey = chunkKey(state.x() >> 4, state.z() >> 4);
        Map<Long, TrackedBlockState> states = byChunk.computeIfAbsent(chunkKey, ignored -> new ConcurrentHashMap<>());
        states.put(state.blockKey(), state);

        Set<Long> revealed = revealedByPlayer.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        if (!state.initiallyMasked()) {
            revealed.add(state.blockKey());
        }
    }

    public void forgetTrackedBlock(Player player, int x, int y, int z) {
        UUID uuid = player.getUniqueId();
        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.get(uuid);
        if (byChunk == null) {
            return;
        }

        long chunkKey = chunkKey(x >> 4, z >> 4);
        Map<Long, TrackedBlockState> states = byChunk.get(chunkKey);
        if (states == null) {
            return;
        }

        long blockKey = TrackedBlockState.packBlockKey(x, y, z);
        states.remove(blockKey);
        if (states.isEmpty()) {
            byChunk.remove(chunkKey);
        }

        Set<Long> revealed = revealedByPlayer.get(uuid);
        if (revealed != null) {
            revealed.remove(blockKey);
        }
    }

    public void rememberChunkSnapshot(Player player, int chunkX, int chunkZ, Collection<TrackedBlockState> trackedStates) {
        if (isPlayerInBlacklistedWorld(player)) {
            clearMaskedState(player);
            return;
        }
        rememberTrackedWorld(player);
        UUID uuid = player.getUniqueId();
        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        long chunkKey = chunkKey(chunkX, chunkZ);

        if (trackedStates.isEmpty()) {
            byChunk.remove(chunkKey);
            Set<Long> revealed = revealedByPlayer.get(uuid);
            if (revealed != null) {
                revealed.removeIf(blockKey -> blockToChunkKey(blockKey) == chunkKey);
            }
            return;
        }

        Map<Long, TrackedBlockState> newMap = new ConcurrentHashMap<>(Math.max(16, trackedStates.size()));
        for (TrackedBlockState state : trackedStates) {
            newMap.put(state.blockKey(), state);
        }
        byChunk.put(chunkKey, newMap);

        Set<Long> revealed = revealedByPlayer.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        revealed.removeIf(blockKey -> blockToChunkKey(blockKey) == chunkKey && !newMap.containsKey(blockKey));
        for (TrackedBlockState state : trackedStates) {
            if (!state.initiallyMasked()) {
                revealed.add(state.blockKey());
            }
        }
    }

    protected final void updateVisibility(Player player) {
        updateKnownWorld(player);
        cachePlayerLocation(player);
        if (isPlayerInBlacklistedWorld(player)) {
            restoreAndClearPlayerState(player);
            return;
        }

        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.get(player.getUniqueId());
        if (byChunk == null || byChunk.isEmpty()) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        Set<Long> revealed = revealedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        Location eye = player.getLocation();
        if (eye.getWorld() == null) {
            return;
        }
        int playerChunkX = eye.getBlockX() >> 4;
        int playerChunkZ = eye.getBlockZ() >> 4;
        int chunkRadius = Math.max(1, (revealRadius >> 4) + 1);

        List<TrackedBlockState> toReveal = new ArrayList<>();
        List<TrackedBlockState> toUnreveal = new ArrayList<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                Map<Long, TrackedBlockState> states = byChunk.get(chunkKey(cx, cz));
                if (states == null || states.isEmpty()) {
                    continue;
                }
                for (TrackedBlockState state : states.values()) {
                    boolean shouldReveal = shouldRevealBlock(eye, world, state);
                    boolean isRevealed = revealed.contains(state.blockKey());
                    if (shouldReveal && !isRevealed) {
                        toReveal.add(state);
                    } else if (!shouldReveal && isRevealed) {
                        toUnreveal.add(state);
                    }
                }
            }
        }

        for (TrackedBlockState state : toReveal) {
            revealed.add(state.blockKey());
            markBypassOutgoingBlockChange(player, state.x(), state.y(), state.z());
            player.sendBlockChange(new Location(world, state.x(), state.y(), state.z()), state.realBlockData());
        }

        for (TrackedBlockState state : toUnreveal) {
            revealed.remove(state.blockKey());
            markBypassOutgoingBlockChange(player, state.x(), state.y(), state.z());
            player.sendBlockChange(new Location(world, state.x(), state.y(), state.z()), state.maskedBlockData());
        }
    }

    public final void restoreAndClearPlayerState(Player player) {
        UUID uuid = player.getUniqueId();
        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.get(uuid);
        World world = player.getWorld();
        String trackedWorld = trackedWorldByPlayer.get(uuid);
        String currentWorld = world == null ? null : world.getKey().toString();
        if (world != null && currentWorld != null && currentWorld.equals(trackedWorld) && byChunk != null && !byChunk.isEmpty()) {
            for (Map<Long, TrackedBlockState> states : byChunk.values()) {
                if (states == null || states.isEmpty()) {
                    continue;
                }
                for (TrackedBlockState state : states.values()) {
                    markBypassOutgoingBlockChange(player, state.x(), state.y(), state.z());
                    player.sendBlockChange(new Location(world, state.x(), state.y(), state.z()), state.realBlockData());
                }
            }
        }
        clearMaskedState(player);
    }

    /**
     * Clears only AntiESP masking/reveal state.
     *
     * Do not remove knownWorldByPlayer/cachedLocationsByPlayer here: packet callbacks may run
     * off the owning region thread, where isPlayerInBlacklistedWorld() intentionally falls back
     * to the last known world. Removing that fallback while the player is inside a blacklisted
     * world makes later chunk/block packets look like they came from an enabled world, which can
     * re-mask packets and cause client flicker/ghost blocks.
     */
    public final void clearMaskedState(Player player) {
        UUID uuid = player.getUniqueId();
        trackedByPlayer.remove(uuid);
        revealedByPlayer.remove(uuid);
        bypassOutgoingBlockChangeByPlayer.remove(uuid);
        timedBypassOutgoingBlockChangeByPlayer.remove(uuid);
        trackedWorldByPlayer.remove(uuid);
    }

    public final void clearPlayerState(Player player) {
        clearMaskedState(player);
        UUID uuid = player.getUniqueId();
        knownWorldByPlayer.remove(uuid);
        cachedLocationsByPlayer.remove(uuid);
    }

    private void rememberTrackedWorld(Player player) {
        World world = player.getWorld();
        if (world != null) {
            trackedWorldByPlayer.put(player.getUniqueId(), world.getKey().toString());
        }
    }

    private boolean isWithinRevealRadius(Location playerLocation, TrackedBlockState state) {
        if (playerLocation.getWorld() == null) {
            return false;
        }
        double dx = (state.x() + 0.5D) - playerLocation.getX();
        double dy = (state.y() + 0.5D) - playerLocation.getY();
        double dz = (state.z() + 0.5D) - playerLocation.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz) <= revealRadiusSquared;
    }

    private boolean shouldRevealBlock(Location eyeLocation, World world, TrackedBlockState state) {
        if (!isWithinRevealRadius(eyeLocation, state)) {
            return false;
        }

        Vector start = eyeLocation.toVector();
        Vector end = new Vector(state.x() + 0.5D, state.y() + 0.5D, state.z() + 0.5D);
        Vector direction = end.clone().subtract(start);
        double distance = direction.length();
        if (distance <= 0.0D) {
            return true;
        }

        direction.multiply(1.0D / distance);
        RayTraceResult result = world.rayTraceBlocks(
                eyeLocation,
                direction,
                distance + 0.25D,
                FluidCollisionMode.NEVER,
                true
        );

        if (result == null) {
            return true;
        }

        Block hitBlock = result.getHitBlock();
        return hitBlock != null
                && hitBlock.getX() == state.x()
                && hitBlock.getY() == state.y()
                && hitBlock.getZ() == state.z();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceSensitiveBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || isPlayerInBlacklistedWorld(player)) {
            return;
        }
        if (!isSensitive(event.getBlockPlaced().getType())) {
            return;
        }

        int x = event.getBlockPlaced().getX();
        int y = event.getBlockPlaced().getY();
        int z = event.getBlockPlaced().getZ();
        markBypassOutgoingBlockChange(player, x, y, z);
        markTimedBypassOutgoingBlockChange(player, x, y, z, 6L);
        forgetTrackedBlock(player, x, y, z);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearPlayerState(event.getPlayer());
        updateKnownWorld(event.getPlayer());
        cachePlayerLocation(event.getPlayer());
        handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handlePlayerQuit(event.getPlayer());
        clearPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayerState(event.getPlayer());
        updateKnownWorld(event.getPlayer());
        cachePlayerLocation(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        restoreAndClearPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        World fromWorld = event.getFrom().getWorld();
        World toWorld = event.getTo().getWorld();
        if (fromWorld != null && toWorld != null && !fromWorld.getUID().equals(toWorld.getUID())) {
            if (isWorldBlacklisted(toWorld)) {
                restoreAndClearPlayerState(event.getPlayer());
            } else {
                clearPlayerState(event.getPlayer());
            }
            knownWorldByPlayer.put(event.getPlayer().getUniqueId(), toWorld.getKey().toString());
            cachedLocationsByPlayer.put(event.getPlayer().getUniqueId(), new CachedPlayerLocation(
                    event.getTo().getX(),
                    event.getTo().getY(),
                    event.getTo().getZ(),
                    toWorld.getKey().toString()
            ));
        }
    }

    protected void handlePlayerJoin(Player player) {
    }

    protected void handlePlayerQuit(Player player) {
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public static long blockToChunkKey(long blockKey) {
        int x = (int) (blockKey >> 38);
        int z = (int) ((blockKey >> 12) & 0x3FFFFFFL);
        if ((x & 0x2000000) != 0) {
            x |= 0xFC000000;
        }
        if ((z & 0x2000000) != 0) {
            z |= 0xFC000000;
        }
        return chunkKey(x >> 4, z >> 4);
    }

    public boolean isBlockRevealedToClient(Player player, int x, int y, int z) {
        if (player == null) {
            return false;
        }

        Set<Long> revealed = revealedByPlayer.get(player.getUniqueId());
        return revealed != null && revealed.contains(TrackedBlockState.packBlockKey(x, y, z));
    }

    public boolean isBlockCurrentlyMasked(Player player, int x, int y, int z) {
        if (player == null) {
            return false;
        }

        Map<Long, Map<Long, TrackedBlockState>> byChunk = trackedByPlayer.get(player.getUniqueId());
        if (byChunk == null || byChunk.isEmpty()) {
            return false;
        }

        Map<Long, TrackedBlockState> states = byChunk.get(chunkKey(x >> 4, z >> 4));
        if (states == null || states.isEmpty()) {
            return false;
        }

        long blockKey = TrackedBlockState.packBlockKey(x, y, z);
        TrackedBlockState state = states.get(blockKey);
        if (state == null) {
            return false;
        }

        Set<Long> revealed = revealedByPlayer.get(player.getUniqueId());
        return revealed == null || !revealed.contains(blockKey);
    }

    public record CachedPlayerLocation(double x, double y, double z, String worldName) {
    }

    private Set<Material> loadSensitiveBlocks() {
        List<String> configured = plugin.getConfig().getStringList("antiesp.sensitive-blocks");
        LinkedHashSet<Material> materials = new LinkedHashSet<>();
        if (configured.isEmpty()) {
            materials.addAll(SensitiveBlockMatcher.defaultSensitiveBlocks());
            return materials;
        }
        for (String entry : configured) {
            Material material = Material.matchMaterial(entry);
            if (material != null && material.isBlock()) {
                materials.add(material);
            }
        }
        if (materials.isEmpty()) {
            materials.addAll(SensitiveBlockMatcher.defaultSensitiveBlocks());
        }
        return materials;
    }

    private Set<String> loadSensitiveSuffixes() {
        List<String> configured = plugin.getConfig().getStringList("antiesp.sensitive-suffixes");
        LinkedHashSet<String> suffixes = new LinkedHashSet<>();
        if (configured.isEmpty()) {
            suffixes.addAll(SensitiveBlockMatcher.defaultSensitiveSuffixes());
            return suffixes;
        }
        for (String entry : configured) {
            if (entry != null && !entry.isBlank()) {
                suffixes.add(entry.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (suffixes.isEmpty()) {
            suffixes.addAll(SensitiveBlockMatcher.defaultSensitiveSuffixes());
        }
        return suffixes;
    }
}
