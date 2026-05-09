package com.maris7.guard.antiesp.config;

import com.maris7.guard.MarisGuard;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public final class CheckConfig {
    private final MarisGuard plugin;
    private FileConfiguration config;

    public CheckConfig(MarisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "checks.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public ConfigurationSection getEsperSection() {
        ConfigurationSection section = config.getConfigurationSection("esper");
        if (section == null) {
            throw new IllegalStateException("checks.yml is missing esper section");
        }
        return section;
    }

    private ConfigurationSection getAutoCheckSection() {
        return getEsperSection().getConfigurationSection("auto-check");
    }

    public boolean isEsperEnabled() {
        return getEsperSection().getBoolean("enabled", true);
    }

    public boolean isEsperPunishable() {
        return getEsperSection().getBoolean("punishable", true);
    }

    public int getEsperPunishmentDelaySeconds() {
        return Math.max(0, getEsperSection().getInt("punishment-delay-in-seconds", 0));
    }

    public int getEsperMaxViolations() {
        return Math.max(1, getEsperSection().getInt("max-violations", 7));
    }

    public List<String> getEsperPunishmentCommands() {
        return getEsperSection().getStringList("punishment-commands");
    }

    public int getEsperDurationSeconds() {
        return Math.max(1, getEsperSection().getInt("duration-seconds", 15));
    }

    public double getEsperSpawnDistance() {
        return Math.max(2.0D, getEsperSection().getDouble("spawn-distance", 9.0D));
    }

    public double getEsperForwardOffset() {
        return Math.max(0.0D, getEsperSection().getDouble("forward-offset", 1.5D));
    }

    public double getEsperTriggerDistance() {
        return Math.max(0.5D, getEsperSection().getDouble("trigger-distance", 1.0D));
    }

    public boolean isEsperAutoCheckEnabled() {
        ConfigurationSection section = getAutoCheckSection();
        return section == null || section.getBoolean("enabled", true);
    }

    public long getEsperAutoCheckIntervalTicks() {
        ConfigurationSection section = getAutoCheckSection();
        return Math.max(60L, section == null ? 40L : section.getLong("interval-ticks", 40L));
    }

    public int getEsperAutoCheckMaxPlayersPerCycle() {
        ConfigurationSection section = getAutoCheckSection();
        return Math.min(2, Math.max(1, section == null ? 3 : section.getInt("max-players-per-cycle", 3)));
    }

    public int getEsperAutoCheckMaxActiveSessions() {
        ConfigurationSection section = getAutoCheckSection();
        return Math.min(4, Math.max(1, section == null ? 8 : section.getInt("max-active-sessions", 8)));
    }

    public int getEsperCooldownSeconds() {
        ConfigurationSection section = getAutoCheckSection();
        return Math.max(90, section == null ? 45 : section.getInt("cooldown-seconds", 45));
    }

    public boolean isEsperRequireSurvival() {
        ConfigurationSection section = getAutoCheckSection();
        return section == null || section.getBoolean("require-survival", true);
    }

    public boolean isEsperRequireRecentMining() {
        ConfigurationSection section = getAutoCheckSection();
        return section == null || section.getBoolean("require-recent-mining", true);
    }

    public int getEsperRecentMiningWindowSeconds() {
        ConfigurationSection section = getAutoCheckSection();
        return Math.max(15, section == null ? 45 : section.getInt("recent-mining-window-seconds", 45));
    }

    public boolean hasEsperMaxY() {
        ConfigurationSection section = getAutoCheckSection();
        return section != null && section.contains("max-y");
    }

    public double getEsperMaxY() {
        ConfigurationSection section = getAutoCheckSection();
        return section == null ? 40.0D : section.getDouble("max-y", 40.0D);
    }
}

