package com.maris7.guard.raytraceantixray.listeners;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import com.maris7.guard.MarisGuard;
import com.maris7.guard.raytraceantixray.data.ChunkBlocks;
import com.maris7.guard.raytraceantixray.data.ChunkPacketKey;
import com.maris7.guard.raytraceantixray.data.LongWrapper;
import com.maris7.guard.raytraceantixray.data.PlayerData;
import com.maris7.guard.raytraceantixray.data.VectorialLocation;
import com.maris7.guard.raytraceantixray.tasks.RayTraceCallable;
import com.maris7.guard.raytraceantixray.util.ChunkKeyUtil;

import net.minecraft.world.level.chunk.LevelChunk;

public final class PacketListener extends PacketListenerAbstract {
    private final MarisGuard plugin;

    public PacketListener(MarisGuard plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            onChunkData(event, (Player) event.getPlayer());
        } else if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            onUnloadChunk(event, (Player) event.getPlayer());
        } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            onRespawn((Player) event.getPlayer());
        }
    }

    private void onChunkData(PacketSendEvent event, Player player) {
        if (!plugin.isEnabled(player.getWorld())) {
            plugin.removePlayerData(player);
            return;
        }

        WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
        Column column = packet.getColumn();
        long chunkKey = ChunkKeyUtil.asLong(column.getX(), column.getZ());
        ConcurrentMap<UUID, PlayerData> playerDataMap = plugin.getPlayerData();
        UUID uniqueId = player.getUniqueId();
        PlayerData playerData = playerDataMap.get(uniqueId);

        if (playerData == null) {
            if (!plugin.validatePlayer(player)) {
                return;
            }
            Location location = player.getEyeLocation();
            playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(location)));
            playerData.setCallable(new RayTraceCallable(plugin, playerData));
            plugin.putPlayerData(player, playerData);
        }

        World dataWorld = playerData.getLocations()[0].getWorld();
        ChunkBlocks chunkBlocks = plugin.getPacketChunkBlocksCache().get(new ChunkPacketKey(dataWorld.getUID(), chunkKey));

        if (chunkBlocks == null) {
            // RayTraceAntiXray is probably not enabled in this world (or other plugins bypass Anti-Xray).
            // We can't determine the world from the chunk packet in this case.
            // Thus we use the player's current (more up to date) world instead.
            Location location = player.getEyeLocation();

            if (!location.getWorld().equals(dataWorld)) {
                // Detected a world change.
                // In the event order listing above, this corresponds to the next chunk packet when RayTraceAntiXray is disabled in the destination world.
                playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(location)));
                playerData.setCallable(new RayTraceCallable(plugin, playerData));
                plugin.putPlayerData(player, playerData);
            }

            return;
        }

        // Get chunk from weak reference.
        LevelChunk chunk = chunkBlocks.getChunk();

        if (chunk == null) {
            plugin.getPacketChunkBlocksCache().remove(new ChunkPacketKey(dataWorld.getUID(), chunkKey), chunkBlocks);
            return;
        }

        CraftWorld world = chunk.getLevel().getWorld();

        if (!world.equals(dataWorld)) {
            // Detected a world change.
            // We need the player's current location to construct a new player data instance.
            Location location = player.getEyeLocation();

            if (!world.equals(location.getWorld())) {
                // The player has changed the world again since this chunk packet was sent.
                return;
            }

            // Renew the player data instance.
            playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(location)));
            playerData.setCallable(new RayTraceCallable(plugin, playerData));
            plugin.putPlayerData(player, playerData);
        }

        // We need to copy the chunk blocks because the same chunk packet could have been sent to multiple players.
        chunkBlocks = new ChunkBlocks(chunk, new HashMap<>(chunkBlocks.getBlocks()));
        playerData.getChunks().put(chunkBlocks.getKey(), chunkBlocks);
    }

    private PlayerData getOrCreatePlayerData(Player player) {
        if (!plugin.validatePlayer(player) || !plugin.isEnabled(player.getWorld())) {
            return null;
        }

        ConcurrentMap<UUID, PlayerData> playerDataMap = plugin.getPlayerData();
        UUID uniqueId = player.getUniqueId();
        PlayerData playerData = playerDataMap.get(uniqueId);

        if (playerData != null && !player.getWorld().equals(playerData.getLocations()[0].getWorld())) {
            playerData.getChunks().clear();
            playerData.getResults().clear();
            playerData = null;
        }

        if (playerData == null) {
            Location location = player.getEyeLocation();
            playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(location)));
            playerData.setCallable(new RayTraceCallable(plugin, playerData));
            plugin.putPlayerData(player, playerData);
        }

        return playerData;
    }

    private void onUnloadChunk(PacketSendEvent event, Player player) {
        if (!plugin.isEnabled(player.getWorld())) {
            plugin.removePlayerData(player);
            return;
        }

        PlayerData playerData = getOrCreatePlayerData(player);
        if (playerData == null) {
            return;
        }

        WrapperPlayServerUnloadChunk packet = new WrapperPlayServerUnloadChunk(event);
        playerData.getChunks().remove(new LongWrapper(ChunkKeyUtil.asLong(packet.getChunkX(), packet.getChunkZ())));
    }

    private void onRespawn(Player player) {
        if (!plugin.isEnabled(player.getWorld())) {
            plugin.removePlayerData(player);
            return;
        }

        PlayerData playerData = getOrCreatePlayerData(player);
        if (playerData == null) {
            return;
        }

        playerData.getChunks().clear();
        playerData.getResults().clear();
    }
}
