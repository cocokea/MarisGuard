package com.maris7.guard.antiesp.base;

import com.maris7.guard.MarisGuard;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BaseTemplateLoader {
    private final MarisGuard plugin;

    public BaseTemplateLoader(MarisGuard plugin) {
        this.plugin = plugin;
    }

    public BaseTemplate load() {
        File file = new File(plugin.getDataFolder(), "base.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection anchor = config.getConfigurationSection("anchor");
        if (anchor == null) {
            throw new IllegalStateException("base.yml is missing anchor section");
        }

        List<BaseTemplateBlock> blocks = new ArrayList<>();
        List<?> rawBlocks = config.getList("blocks");
        if (rawBlocks == null || rawBlocks.isEmpty()) {
            throw new IllegalStateException("base.yml does not contain any blocks");
        }

        for (Object raw : rawBlocks) {
            ConfigurationSection section = null;
            if (raw instanceof ConfigurationSection configurationSection) {
                section = configurationSection;
            } else if (raw instanceof Map<?, ?> map) {
                YamlConfiguration temp = new YamlConfiguration();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        temp.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                section = temp;
            }

            if (section == null) {
                continue;
            }

            String blockDataString = section.getString("blockData", "minecraft:air");
            BlockData blockData = Bukkit.createBlockData(blockDataString);
            if (blockData.getMaterial().isAir()) {
                continue;
            }

            blocks.add(new BaseTemplateBlock(
                    section.getInt("x"),
                    section.getInt("y"),
                    section.getInt("z"),
                    blockData
            ));
        }

        return new BaseTemplate(anchor.getInt("x"), anchor.getInt("y"), anchor.getInt("z"), List.copyOf(blocks));
    }
}

