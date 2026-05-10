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
        add(blocks, 0, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]");
        add(blocks, -2, 0, 0, "minecraft:end_portal_frame[eye=false,facing=north]");
        add(blocks, 2, 0, 0, "minecraft:crafting_table");
        add(blocks, 0, 0, 2, "minecraft:enchanting_table");
        add(blocks, 0, 0, -2, "minecraft:daylight_detector[inverted=false,power=0]");
        add(blocks, -2, 1, 1, "minecraft:comparator[facing=north,mode=compare,powered=false]");
        add(blocks, 2, 1, 1, "minecraft:repeater[delay=1,facing=north,locked=false,powered=false]");
        add(blocks, -2, 1, -1, "minecraft:note_block[instrument=harp,note=0,powered=false]");
        add(blocks, 2, 1, -1, "minecraft:piston[extended=false,facing=up]");
        add(blocks, 0, 1, 0, "minecraft:sticky_piston[extended=false,facing=up]");

        return new BaseTemplate(0, 0, 0, List.copyOf(blocks));
    }

    private static void add(List<BaseTemplateBlock> blocks, int x, int y, int z, String blockDataString) {
        BlockData blockData = Bukkit.createBlockData(blockDataString);
        if (!blockData.getMaterial().isAir()) {
            blocks.add(new BaseTemplateBlock(x, y, z, blockData));
        }
    }
}

