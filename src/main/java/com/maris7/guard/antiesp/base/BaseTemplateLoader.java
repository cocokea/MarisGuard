package com.maris7.guard.antiesp.base;

import com.maris7.guard.MarisGuard;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import java.util.ArrayList;
import java.util.List;

public final class BaseTemplateLoader {
    private final MarisGuard plugin;

    public BaseTemplateLoader(MarisGuard plugin) {
        this.plugin = plugin;
    }

    public BaseTemplate load() {
        List<BaseTemplateBlock> blocks = new ArrayList<>();
        add(blocks, -1, 0, 0, "minecraft:barrel[facing=up,open=false]");
        add(blocks, 1, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        add(blocks, 0, 0, 2, "minecraft:ender_chest[facing=north,waterlogged=false]");
        add(blocks, -2, 1, 1, "minecraft:spawner");
        add(blocks, 2, 1, 1, "minecraft:trial_spawner");
        add(blocks, 0, 1, -2, "minecraft:shulker_box[facing=up]");
        add(blocks, -1, 1, -1, "minecraft:decorated_pot[facing=north,waterlogged=false]");
        add(blocks, 1, 1, -1, "minecraft:beehive[facing=north,honey_level=5]");

        return new BaseTemplate(0, 0, 0, List.copyOf(blocks));
    }

    private static void add(List<BaseTemplateBlock> blocks, int x, int y, int z, String blockDataString) {
        BlockData blockData = Bukkit.createBlockData(blockDataString);
        if (!blockData.getMaterial().isAir()) {
            blocks.add(new BaseTemplateBlock(x, y, z, blockData));
        }
    }
}

