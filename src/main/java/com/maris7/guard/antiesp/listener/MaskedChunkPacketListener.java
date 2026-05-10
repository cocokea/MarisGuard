package com.maris7.guard.antiesp.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.maris7.guard.antiesp.service.AbstractPlayerRevealService;
import com.maris7.guard.antiesp.service.SensitiveBlockMatcher;
import com.maris7.guard.antiesp.service.TrackedBlockState;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MaskedChunkPacketListener extends PacketListenerAbstract {

    private final AbstractPlayerRevealService revealService;
    private final int revealRadiusSquared;
    private final Material maskMaterial;
    private final int maskStateId;
    private final WrappedBlockState maskBlockState;
    private static final int STATE_CACHE_MAX_SIZE = 4096;
    private final Map<Integer, CachedBlockState> stateCache = java.util.Collections.synchronizedMap(new LinkedHashMap<Integer, CachedBlockState>(STATE_CACHE_MAX_SIZE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CachedBlockState> eldest) {
            return size() > STATE_CACHE_MAX_SIZE;
        }
    });

    public MaskedChunkPacketListener(AbstractPlayerRevealService revealService) {
        this.revealService = revealService;
        this.revealRadiusSquared = revealService.getRevealRadiusSquared();
        this.maskMaterial = revealService.getMaskMaterial();
        this.maskBlockState = SpigotConversionUtil.fromBukkitBlockData(maskMaterial.createBlockData());
        this.maskStateId = maskBlockState.getGlobalId();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        final var packetType = event.getPacketType();
        if (packetType == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event);
            return;
        }
        if (packetType == PacketType.Play.Server.BLOCK_CHANGE) {
            handleBlockChange(event);
            return;
        }
        if (packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            handleMultiBlockChange(event);
            return;
        }
        if (packetType == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            handleBlockEntityData(event);
        }
    }

    private void handleChunkData(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }

        if (revealService.isPlayerInBlacklistedWorld(player)) {
            revealService.clearMaskedState(player);
            return;
        }
        final WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        final Column column = wrapper.getColumn();
        if (column == null) {
            return;
        }

        final BaseChunk[] sections = column.getChunks();
        if (sections == null || sections.length == 0) {
            return;
        }

        final int minHeight = event.getUser().getMinWorldHeight();
        final int sectionBaseY = minHeight >> 4;
        final int chunkX = column.getX();
        final int chunkZ = column.getZ();
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        final AbstractPlayerRevealService.CachedPlayerLocation playerLocation = revealService.getCachedLocation(player.getUniqueId());

        final List<TrackedBlockState> trackedStates = new ArrayList<>();
        Set<Long> maskedBlockKeys = null;
        boolean mutated = false;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            final BaseChunk section = sections[sectionIndex];
            if (section == null) {
                continue;
            }

            final int worldSectionY = sectionBaseY + sectionIndex;
            final int baseY = worldSectionY << 4;

            for (int y = 0; y < 16; y++) {
                final int worldY = baseY + y;
                for (int z = 0; z < 16; z++) {
                    final int worldZ = baseZ + z;
                    for (int x = 0; x < 16; x++) {
                        final WrappedBlockState state = section.get(x, y, z, true);
                        final CachedBlockState cachedState = getCachedState(state);
                        if (!cachedState.sensitive()) {
                            continue;
                        }

                        final int worldX = baseX + x;
                        if (revealService.isVirtualBlockBypassed(player, worldX, worldY, worldZ)) {
                            continue;
                        }
                        final boolean shouldMask = !revealService.isBlockRevealedToClient(player, worldX, worldY, worldZ)
                                && !isWithinRevealRadius(playerLocation, worldX, worldY, worldZ);

                        trackedStates.add(TrackedBlockState.create(
                                worldX,
                                worldY,
                                worldZ,
                                cachedState.blockData(),
                                maskMaterial,
                                shouldMask
                        ));

                        if (shouldMask) {
                            section.set(x, y, z, maskStateId);
                            if (maskedBlockKeys == null) {
                                maskedBlockKeys = new HashSet<>();
                            }
                            maskedBlockKeys.add(TrackedBlockState.packBlockKey(worldX, worldY, worldZ));
                            mutated = true;
                        }
                    }
                }
            }
        }

        revealService.rememberChunkSnapshot(player, chunkX, chunkZ, trackedStates);

        if (!mutated) {
            return;
        }

        wrapper.setColumn(rebuildColumn(column, sections, maskedBlockKeys == null ? Set.of() : maskedBlockKeys));
        wrapper.write();
        event.markForReEncode(true);
    }

    private void handleBlockChange(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }

        if (revealService.isPlayerInBlacklistedWorld(player)) {
            revealService.clearMaskedState(player);
            return;
        }

        final WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        final Vector3i pos = wrapper.getBlockPosition();
        final WrappedBlockState state = wrapper.getBlockState();
        if (pos == null || state == null) {
            return;
        }

        if (revealService.consumeBypassOutgoingBlockChange(player, pos.x, pos.y, pos.z)) {
            return;
        }
        if (revealService.isVirtualBlockBypassed(player, pos.x, pos.y, pos.z)) {
            return;
        }

        final CachedBlockState cachedState = getCachedState(state);
        if (!cachedState.sensitive()) {
            revealService.forgetTrackedBlock(player, pos.x, pos.y, pos.z);
            return;
        }

        final AbstractPlayerRevealService.CachedPlayerLocation playerLocation = revealService.getCachedLocation(player.getUniqueId());
        final boolean shouldMask = !revealService.isBlockRevealedToClient(player, pos.x, pos.y, pos.z)
                && !isWithinRevealRadius(playerLocation, pos.x, pos.y, pos.z);
        revealService.upsertTrackedBlock(player, TrackedBlockState.create(
                pos.x, pos.y, pos.z, cachedState.blockData(), maskMaterial, shouldMask
        ));

        if (shouldMask) {
            wrapper.setBlockState(maskBlockState);
            wrapper.write();
            event.markForReEncode(true);
        }
    }

    private void handleMultiBlockChange(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }

        if (revealService.isPlayerInBlacklistedWorld(player)) {
            revealService.clearMaskedState(player);
            return;
        }

        final WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        final WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
        if (blocks == null || blocks.length == 0) {
            return;
        }

        final AbstractPlayerRevealService.CachedPlayerLocation playerLocation = revealService.getCachedLocation(player.getUniqueId());
        boolean mutated = false;

        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
            final int x = block.getX();
            final int y = block.getY();
            final int z = block.getZ();

            if (revealService.consumeBypassOutgoingBlockChange(player, x, y, z)) {
                continue;
            }
            if (revealService.isVirtualBlockBypassed(player, x, y, z)) {
                continue;
            }

            final CachedBlockState cachedState = getCachedState(block.getBlockState(event.getUser().getClientVersion()));
            if (!cachedState.sensitive()) {
                revealService.forgetTrackedBlock(player, x, y, z);
                continue;
            }

            final boolean shouldMask = !revealService.isBlockRevealedToClient(player, x, y, z)
                    && !isWithinRevealRadius(playerLocation, x, y, z);
            revealService.upsertTrackedBlock(player, TrackedBlockState.create(
                    x, y, z, cachedState.blockData(), maskMaterial, shouldMask
            ));

            if (shouldMask) {
                block.setBlockState(maskBlockState);
                mutated = true;
            }
        }

        if (mutated) {
            wrapper.setBlocks(blocks);
            wrapper.write();
            event.markForReEncode(true);
        }
    }

    private void handleBlockEntityData(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !player.isOnline()) {
            return;
        }

        if (revealService.isPlayerInBlacklistedWorld(player)) {
            revealService.clearMaskedState(player);
            return;
        }

        final WrapperPlayServerBlockEntityData wrapper = new WrapperPlayServerBlockEntityData(event);
        final Vector3i pos = wrapper.getPosition();
        if (pos == null) {
            return;
        }

        if (revealService.isVirtualBlockBypassed(player, pos.x, pos.y, pos.z)) {
            return;
        }

        if (revealService.isBlockCurrentlyMasked(player, pos.x, pos.y, pos.z)) {
            event.setCancelled(true);
        }
    }

    private Column rebuildColumn(Column source, BaseChunk[] sections, Set<Long> maskedBlockKeys) {
        TileEntity[] tileEntities = source.getTileEntities();
        if (tileEntities != null && tileEntities.length > 0 && !maskedBlockKeys.isEmpty()) {
            final List<TileEntity> kept = new ArrayList<>(tileEntities.length);
            for (TileEntity tileEntity : tileEntities) {
                final long blockKey = TrackedBlockState.packBlockKey(tileEntity.getX(), tileEntity.getY(), tileEntity.getZ());
                if (!maskedBlockKeys.contains(blockKey)) {
                    kept.add(tileEntity);
                }
            }
            tileEntities = kept.toArray(TileEntity[]::new);
        }

        if (source.hasBiomeData()) {
            final int[] biomeInts = source.getBiomeDataInts();
            if (biomeInts != null) {
                if (source.hasHeightMaps()) {
                    return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, source.getHeightMaps(), biomeInts);
                }
                return new Column(source.getX(), source.getZ(), source.isFullChunk(), sections, tileEntities, biomeInts);
            }
            final byte[] biomeBytes = source.getBiomeDataBytes();
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

    private CachedBlockState getCachedState(WrappedBlockState state) {
        final int globalId = state.getGlobalId();
        return stateCache.computeIfAbsent(globalId, ignored -> {
            final BlockData blockData = SpigotConversionUtil.toBukkitBlockData(state);
            final Material material = blockData.getMaterial();
            return new CachedBlockState(SensitiveBlockMatcher.isSensitive(material), blockData);
        });
    }

    private boolean isWithinRevealRadius(AbstractPlayerRevealService.CachedPlayerLocation playerLocation, int x, int y, int z) {
        if (playerLocation == null) {
            return false;
        }

        final double dx = (x + 0.5D) - playerLocation.x();
        final double dy = (y + 0.5D) - playerLocation.y();
        final double dz = (z + 0.5D) - playerLocation.z();
        return (dx * dx) + (dy * dy) + (dz * dz) <= revealRadiusSquared;
    }

    private record CachedBlockState(boolean sensitive, BlockData blockData) {
    }
}
