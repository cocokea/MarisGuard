package com.maris7.guard.entity;

import com.maris7.guard.MarisGuard;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuplicateEntityUuidGuard implements Listener {
    private final MarisGuard plugin;
    private final Map<UUID, Map<UUID, WeakReference<Entity>>> entitiesByWorld = new ConcurrentHashMap<>();

    public DuplicateEntityUuidGuard(MarisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        guard(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            guard(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        World world = event.getWorld();
        Map<UUID, WeakReference<Entity>> known = entitiesByWorld.get(world.getUID());
        if (known == null) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            WeakReference<Entity> reference = known.get(entity.getUniqueId());
            if (reference != null && reference.get() == entity) {
                known.remove(entity.getUniqueId(), reference);
            }
        }
        if (known.isEmpty()) {
            entitiesByWorld.remove(world.getUID(), known);
        }
    }

    private void guard(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }

        World world = entity.getWorld();
        Map<UUID, WeakReference<Entity>> known = entitiesByWorld.computeIfAbsent(world.getUID(), ignored -> new ConcurrentHashMap<>());
        UUID uuid = entity.getUniqueId();

        WeakReference<Entity> previousReference = known.get(uuid);
        Entity previous = previousReference == null ? null : previousReference.get();
        if (previous == null || previous == entity || !previous.isValid() || previous.isDead()) {
            known.put(uuid, new WeakReference<>(entity));
            return;
        }

        plugin.getLogger().warning("Removing duplicate entity UUID " + uuid
                + " from " + entity.getType()
                + " at " + format(entity)
                + "; keeping existing entity at " + format(previous));
        entity.remove();
    }

    private String format(Entity entity) {
        return entity.getWorld().getName()
                + " " + entity.getLocation().getBlockX()
                + "," + entity.getLocation().getBlockY()
                + "," + entity.getLocation().getBlockZ();
    }
}
