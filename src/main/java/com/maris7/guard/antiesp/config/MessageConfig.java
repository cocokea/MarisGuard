package com.maris7.guard.antiesp.config;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MessageConfig {
    private final MarisGuard plugin;
    private FileConfiguration config;

    public MessageConfig(MarisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "message.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String message = config.getString(path, path);
        String prefix = config.getString("prefix", "");
        if (message != null) {
            message = message.replace("%prefix%", prefix);
        }
        return ColorUtil.colorize(message);
    }

    public String get(String path, String... replacements) {
        String message = get(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    public List<String> getList(String path) {
        List<String> lines = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            lines.add(ColorUtil.colorize(line));
        }
        return lines;
    }
}

