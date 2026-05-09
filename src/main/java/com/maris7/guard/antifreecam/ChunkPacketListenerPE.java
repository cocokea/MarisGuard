package com.maris7.guard.antifreecam;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ChunkPacketListenerPE extends PacketListenerAbstract {
    private final MarisFreeCamPlugin freeCam;
    private final WrappedBlockState stoneState = SpigotConversionUtil.fromBukkitBlockData(Material.STONE.createBlockData());
    private final WrappedBlockState deepslateState = SpigotConversionUtil.fromBukkitBlockData(Material.DEEPSLATE.createBlockData());

    public ChunkPacketListenerPE(MarisFreeCamPlugin freeCam) {
        this.freeCam = freeCam;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }
        if (!freeCam.isEnabledFor(player)) {
            return;
        }

        int hideBelowY = freeCam.getHideBelowY(player);
        if (hideBelowY == Integer.MIN_VALUE) {
            return;
        }

        var packetType = event.getPacketType();
        if (packetType == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event, hideBelowY);
            return;
        }
        if (packetType == PacketType.Play.Server.BLOCK_CHANGE) {
            handleBlockChange(event, hideBelowY);
            return;
        }
        if (packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            handleMultiBlockChange(event, hideBelowY);
            return;
        }
        if (packetType == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            handleBlockEntityData(event, hideBelowY);
            return;
        }
        if (packetType == PacketType.Play.Server.BLOCK_ACTION) {
            handleBlockAction(event, hideBelowY);
        }
    }

    private void handleChunkData(PacketSendEvent event, int hideBelowY) {
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        Column column = wrapper.getColumn();
        if (column == null) {
            return;
        }

        BaseChunk[] sections = column.getChunks();
        if (sections == null || sections.length == 0) {
            return;
        }

        int minHeight = event.getUser().getMinWorldHeight();
        int sectionBaseY = minHeight >> 4;
        boolean mutated = false;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            BaseChunk section = sections[sectionIndex];
            if (section == null) {
                continue;
            }

            int worldSectionY = sectionBaseY + sectionIndex;
            int baseY = worldSectionY << 4;
            if (baseY >= hideBelowY) {
                continue;
            }

            for (int y = 0; y < 16; y++) {
                int worldY = baseY + y;
                if (worldY >= hideBelowY) {
                    continue;
                }
                int stateId = maskStateForY(worldY).getGlobalId();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.set(x, y, z, stateId);
                    }
                }
            }
            mutated = true;
        }

        if (!mutated) {
            return;
        }

        wrapper.setColumn(rebuildColumn(column, sections, hideBelowY));
        wrapper.write();
        event.markForReEncode(true);
    }

    private void handleBlockChange(PacketSendEvent event, int hideBelowY) {
        WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        Vector3i pos = wrapper.getBlockPosition();
        if (pos == null || pos.y >= hideBelowY) {
            return;
        }
        wrapper.setBlockState(maskStateForY(pos.y));
        wrapper.write();
        event.markForReEncode(true);
    }

    private void handleMultiBlockChange(PacketSendEvent event, int hideBelowY) {
        WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
        if (blocks == null || blocks.length == 0) {
            return;
        }

        boolean mutated = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
            if (block.getY() >= hideBelowY) {
                continue;
            }
            block.setBlockState(maskStateForY(block.getY()));
            mutated = true;
        }

        if (!mutated) {
            return;
        }

        wrapper.setBlocks(blocks);
        wrapper.write();
        event.markForReEncode(true);
    }

    private void handleBlockEntityData(PacketSendEvent event, int hideBelowY) {
        WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
        Vector3i pos = wrapper.getPosition();
        if (pos != null && pos.y < hideBelowY) {
            event.setCancelled(true);
        }
    }

    private void handleBlockAction(PacketSendEvent event, int hideBelowY) {
        WrapperPlayServerBlockAction wrapper = new WrapperPlayServerBlockAction(event);
        Vector3i pos = wrapper.getBlockPosition();
        if (pos != null && pos.y < hideBelowY) {
            event.setCancelled(true);
        }
    }

    private Column rebuildColumn(Column source, BaseChunk[] sections, int hideBelowY) {
        TileEntity[] tileEntities = source.getTileEntities();
        if (tileEntities != null && tileEntities.length > 0) {
            List<TileEntity> kept = new ArrayList<>(tileEntities.length);
            for (TileEntity tileEntity : tileEntities) {
                if (tileEntity.getY() >= hideBelowY) {
                    kept.add(tileEntity);
                }
            }
            tileEntities = kept.toArray(TileEntity[]::new);
        }

        if (source.hasBiomeData()) {
            int[] biomeInts = source.getBiomeDataInts();
            if (biomeInts != null) {
                if (source.hasHeightMaps()) {
                    return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, source.getHeightMaps(), biomeInts);
                }
                return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, biomeInts);
            }
            byte[] biomeBytes = source.getBiomeDataBytes();
            if (source.hasHeightMaps()) {
                return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, source.getHeightMaps(), biomeBytes);
            }
            return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, biomeBytes);
        }

        if (source.hasHeightMaps()) {
            return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, source.getHeightMaps());
        }
        return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities);
    }

    private WrappedBlockState maskStateForY(int worldY) {
        return worldY < 0 ? deepslateState : stoneState;
    }
}
