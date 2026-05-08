package com.maris7.guard.util;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.Collection;
import java.util.Locale;

public final class WorldNameMatcher {
    private WorldNameMatcher() {
    }

    public static boolean contains(Collection<String> configuredWorlds, World world) {
        if (configuredWorlds == null || world == null) {
            return false;
        }
        for (String configured : configuredWorlds) {
            if (matches(world, configured)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsKey(Collection<String> configuredWorlds, String worldKeyOrName) {
        if (configuredWorlds == null || worldKeyOrName == null) {
            return false;
        }
        for (String configured : configuredWorlds) {
            if (matchesKeyOrName(worldKeyOrName, configured)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(World world, String configured) {
        if (world == null || isBlank(configured)) {
            return false;
        }
        NamespacedKey key = world.getKey();
        return equals(configured, world.getName())
                || equals(configured, key.toString())
                || equals(configured, key.getKey())
                || equals(configured, tail(key.getKey()));
    }

    public static boolean matchesKeyOrName(String worldKeyOrName, String configured) {
        if (isBlank(worldKeyOrName) || isBlank(configured)) {
            return false;
        }
        String withoutNamespace = stripNamespace(worldKeyOrName);
        String configuredWithoutNamespace = stripNamespace(configured);
        return equals(configured, worldKeyOrName)
                || equals(configured, withoutNamespace)
                || equals(configured, tail(withoutNamespace))
                || equals(configuredWithoutNamespace, worldKeyOrName)
                || equals(configuredWithoutNamespace, withoutNamespace)
                || equals(tail(configuredWithoutNamespace), tail(withoutNamespace));
    }

    private static boolean equals(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String stripNamespace(String value) {
        int split = value.indexOf(':');
        return split < 0 ? value : value.substring(split + 1);
    }

    private static String tail(String value) {
        String normalized = value.replace('\\', '/');
        int split = normalized.lastIndexOf('/');
        return split < 0 ? normalized : normalized.substring(split + 1);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
