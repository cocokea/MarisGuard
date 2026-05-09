package com.maris7.guard.antiesp.service;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SensitiveBlockMatcher {

    private static final Set<Material> EXACT = EnumSet.of(
            Material.BEACON,
            Material.BREWING_STAND,
            Material.CHEST,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.COMPARATOR,
            Material.CONDUIT,
            Material.DAYLIGHT_DETECTOR,
            Material.DISPENSER,
            Material.DROPPER,
            Material.ENCHANTING_TABLE,
            Material.ENDER_CHEST,
            Material.END_PORTAL_FRAME,
            Material.FURNACE,
            Material.LECTERN,
            Material.HOPPER,
            Material.JUKEBOX,
            Material.NOTE_BLOCK,
            Material.REPEATER,
            Material.TRAPPED_CHEST,
            Material.CRAFTING_TABLE,
            Material.BARREL,
            Material.BELL,
            Material.JIGSAW,
            Material.BEEHIVE,
            Material.BEE_NEST,
            Material.SCULK_SENSOR,
            Material.CALIBRATED_SCULK_SENSOR,
            Material.SCULK_CATALYST,
            Material.SCULK_SHRIEKER,
            Material.CHISELED_BOOKSHELF,
            Material.DECORATED_POT,
            Material.SMOKER,
            Material.BLAST_FURNACE,
            Material.CRAFTER,
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.PISTON,
            Material.STICKY_PISTON
    );

    private SensitiveBlockMatcher() {
    }

    public static Set<Material> defaultSensitiveBlocks() {
        return new LinkedHashSet<>(EXACT);
    }

    public static Set<String> defaultSensitiveSuffixes() {
        return Set.of(
                "_SHULKER_BOX",
                "_BED",
                "_BANNER",
                "_WALL_BANNER",
                "_HEAD",
                "_WALL_HEAD",
                "_SKULL",
                "_WALL_SKULL"
        );
    }

    public static boolean isSensitive(Material material) {
        if (EXACT.contains(material)) {
            return true;
        }

        final String name = material.name().toUpperCase(Locale.ROOT);
        for (String suffix : defaultSensitiveSuffixes()) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}

