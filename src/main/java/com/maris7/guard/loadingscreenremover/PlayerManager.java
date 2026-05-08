package com.maris7.guard.loadingscreenremover;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final Set<UUID> changingWorlds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recentlyDied = ConcurrentHashMap.newKeySet();
    private final Map<UUID, WorldSnapshot> lastKnownWorlds = new ConcurrentHashMap<>();

    public boolean isPlayerChangingWorlds(@NotNull Player player) {
        return changingWorlds.contains(player.getUniqueId());
    }

    public void addChangingWorldPlayer(@NotNull Player player) {
        changingWorlds.add(player.getUniqueId());
    }

    public void removeChangingWorldPlayer(@NotNull Player player) {
        changingWorlds.remove(player.getUniqueId());
    }

    public void addRecentlyDiedPlayer(@NotNull Player player) {
        recentlyDied.add(player.getUniqueId());
    }

    public boolean consumeRecentlyDiedPlayer(@NotNull Player player) {
        return recentlyDied.remove(player.getUniqueId());
    }

    public boolean hasRecentlyDiedPlayer(@NotNull Player player) {
        return recentlyDied.contains(player.getUniqueId());
    }

    public void removeRecentlyDiedPlayer(@NotNull Player player) {
        recentlyDied.remove(player.getUniqueId());
    }

    public Optional<WorldSnapshot> getLastKnownWorld(@NotNull Player player) {
        return Optional.ofNullable(lastKnownWorlds.get(player.getUniqueId()));
    }

    public void updateLastKnownWorld(@NotNull Player player) {
        lastKnownWorlds.put(player.getUniqueId(), WorldSnapshot.from(player.getWorld()));
    }

    public void updateLastKnownWorld(@NotNull Player player, @NotNull World world) {
        lastKnownWorlds.put(player.getUniqueId(), WorldSnapshot.from(world));
    }

    public void clearPlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        changingWorlds.remove(uuid);
        recentlyDied.remove(uuid);
        lastKnownWorlds.remove(uuid);
    }

    public static final class WorldSnapshot {
        private final String key;
        private final World.Environment environment;

        private WorldSnapshot(String key, World.Environment environment) {
            this.key = key;
            this.environment = environment;
        }

        public static WorldSnapshot from(World world) {
            return new WorldSnapshot(world.getKey().toString(), world.getEnvironment());
        }

        public String getKey() {
            return key;
        }

        public World.Environment getEnvironment() {
            return environment;
        }

        @Override
        public String toString() {
            return key + "(" + environment + ")";
        }
    }
}
