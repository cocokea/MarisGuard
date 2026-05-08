package com.maris7.guard.raytraceantixray.listeners;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class WorldListener implements Listener {
    private final MarisGuard plugin;

    public WorldListener(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        install(event.getWorld());
    }

    public void install(World world) {
        if (world == null || !plugin.isEnabled(world)) {
            return;
        }

        ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        if (serverLevel.chunkPacketBlockController instanceof ChunkPacketBlockControllerAntiXray) {
            return;
        }

        {
            FileConfiguration config = plugin.getConfig();
            String worldName = plugin.worldSettingsKey(world);
            boolean rayTraceThirdPerson = config.getBoolean("world-settings." + worldName + ".anti-xray.ray-trace-third-person", config.getBoolean("world-settings.default.anti-xray.ray-trace-third-person"));
            double rayTraceDistance = Math.max(config.getDouble("world-settings." + worldName + ".anti-xray.ray-trace-distance", config.getDouble("world-settings.default.anti-xray.ray-trace-distance")), 0.);
            boolean rehideBlocks = config.getBoolean("world-settings." + worldName + ".anti-xray.rehide-blocks", config.getBoolean("world-settings.default.anti-xray.rehide-blocks"));
            double rehideDistance = Math.max(config.getDouble("world-settings." + worldName + ".anti-xray.rehide-distance", config.getDouble("world-settings.default.anti-xray.rehide-distance")), 0.);
            int maxRayTraceBlockCountPerChunk = Math.min(Math.max(config.getInt("world-settings." + worldName + ".anti-xray.max-ray-trace-block-count-per-chunk", config.getInt("world-settings.default.anti-xray.max-ray-trace-block-count-per-chunk")), 0), 24);
            List<String> rayTraceBlocks = config.getList("world-settings." + worldName + ".anti-xray.ray-trace-blocks", config.getList("world-settings.default.anti-xray.ray-trace-blocks")).stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
            ChunkPacketBlockControllerAntiXray controller = new ChunkPacketBlockControllerAntiXray(plugin, rayTraceThirdPerson, rayTraceDistance, rehideBlocks, rehideDistance, maxRayTraceBlockCountPerChunk, rayTraceBlocks.isEmpty() ? null : rayTraceBlocks, serverLevel, MinecraftServer.getServer().executor);

            try {
                Field field = Level.class.getDeclaredField("chunkPacketBlockController");
                field.setAccessible(true);
                field.set(serverLevel, controller);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
