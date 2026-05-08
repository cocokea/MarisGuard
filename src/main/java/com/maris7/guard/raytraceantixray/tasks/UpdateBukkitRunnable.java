package com.maris7.guard.raytraceantixray.tasks;

import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.raytraceantixray.data.ChunkBlocks;
import com.maris7.guard.raytraceantixray.data.LongWrapper;
import com.maris7.guard.raytraceantixray.data.PlayerData;
import com.maris7.guard.raytraceantixray.data.Result;
import com.maris7.guard.raytraceantixray.data.VectorialLocation;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class UpdateBukkitRunnable extends BukkitRunnable implements Consumer<ScheduledTask> {
    private final MarisGuard plugin;
    private final Player player;

    public UpdateBukkitRunnable(MarisGuard plugin) {
        this(plugin, null);
    }

    public UpdateBukkitRunnable(MarisGuard plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void run() {
        if (player == null) {
            plugin.getServer().getOnlinePlayers().forEach(this::update);
        } else {
            update(player);
        }
    }

    @Override
    public void accept(ScheduledTask t) {
        run();
    }

    public void update(Player player) {
        if (!plugin.isEnabled(player.getWorld())) {
            PlayerData disabledWorldData = plugin.removePlayerData(player);
            if (disabledWorldData != null) {
                disabledWorldData.getChunks().clear();
                disabledWorldData.getResults().clear();
            }
            return;
        }

        PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());

        if (playerData == null) {
            if (!plugin.validatePlayer(player)) {
                return;
            }
            playerData = new PlayerData(MarisGuard.getLocations(player, new VectorialLocation(player.getEyeLocation())));
            playerData.setCallable(new RayTraceCallable(plugin, playerData));
            plugin.putPlayerData(player, playerData);
        }

        World world = playerData.getLocations()[0].getWorld();

        if (!player.getWorld().equals(world)) {
            playerData.getChunks().clear();
            playerData.getResults().clear();
            return;
        }

        ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        Environment environment = world.getEnvironment();
        Queue<Result> results = playerData.getResults();
        Result result;

        while ((result = results.poll()) != null) {
            ChunkBlocks chunkBlocks = result.getChunkBlocks();

            // Check if the client still has the chunk loaded and if it wasn't resent in the meantime.
            // Note that even if this check passes, the server could have already unloaded or resent the chunk but the corresponding packet is still in the packet queue.
            // Technically the null check isn't necessary but we don't need to send an update packet because the client will unload the chunk.
            if (chunkBlocks.getChunk() == null || chunks.get(chunkBlocks.getKey()) != chunkBlocks) {
                continue;
            }

            BlockPos block = result.getBlock();

            // Similar to the null check above, this check isn't actually necessary.
            // However, we don't need to send an update packet because the client will unload the chunk.
            // Thus we can avoid loading the chunk just for the update packet.
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }

            BlockState blockState;
            BlockEntity blockEntity = null;

            if (result.isVisible()) {
                blockState = serverLevel.getBlockState(block);

                if (blockState.hasBlockEntity()) {
                    blockEntity = serverLevel.getBlockEntity(block);
                }
            } else if (environment == Environment.NETHER) {
                blockState = Blocks.NETHERRACK.defaultBlockState();
            } else if (environment == Environment.THE_END) {
                blockState = Blocks.END_STONE.defaultBlockState();
            } else if (block.getY() < 0) {
                blockState = Blocks.DEEPSLATE.defaultBlockState();
            } else {
                blockState = Blocks.STONE.defaultBlockState();
            }

            // We can't send the packet normally (through the packet queue).
            // We bypass the packet queue since our calculations are based on the packet state (not the server state) as seen by the packet listener.
            // As described above, the packet queue could for example already contain a chunk unload packet.
            // Thus we send our packet immediately before that.
            plugin.getRayTracePacketBridge().sendPacketImmediately(player, new ClientboundBlockUpdatePacket(block, blockState));

            if (blockEntity != null) {
                Packet<ClientGamePacketListener> packet = blockEntity.getUpdatePacket();

                if (packet != null) {
                    plugin.getRayTracePacketBridge().sendPacketImmediately(player, packet);
                }
            }
        }
    }
}
