package com.maris7.guard.antiesp.menu;

import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class EsperMenuListener implements Listener {
    private final AbstractEsperManager esperManager;

    public EsperMenuListener(AbstractEsperManager esperManager) {
        this.esperManager = esperManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof EsperMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (rawSlot == EsperMenu.PREVIOUS_SLOT) {
            EsperMenu.open(player, esperManager, holder.page() - 1);
            return;
        }
        if (rawSlot == EsperMenu.NEXT_SLOT) {
            EsperMenu.open(player, esperManager, holder.page() + 1);
            return;
        }
        if (rawSlot >= EsperMenu.PAGE_SIZE) {
            return;
        }

        List<AbstractEsperManager.FlaggedPlayerEntry> flaggedPlayers = esperManager.getFlaggedPlayers();
        int index = holder.page() * EsperMenu.PAGE_SIZE + rawSlot;
        if (index < 0 || index >= flaggedPlayers.size()) {
            return;
        }

        Player target = Bukkit.getPlayer(flaggedPlayers.get(index).uniqueId());
        if (target == null || !target.isOnline()) {
            player.sendMessage(esperManager.message("command.target-offline"));
            player.closeInventory();
            return;
        }

        target.getScheduler().run(esperManager.plugin(), task -> {
            if (!target.isOnline() || !target.isValid()) {
                player.getScheduler().run(esperManager.plugin(), closeTask -> {
                    player.sendMessage(esperManager.message("command.target-offline"));
                    player.closeInventory();
                }, null);
                return;
            }
            Location destination = target.getLocation().clone();
            player.getScheduler().run(esperManager.plugin(), teleportTask -> {
                if (!player.isOnline() || !player.isValid()) {
                    return;
                }
                player.teleportAsync(destination);
                player.closeInventory();
            }, null);
        }, null);
    }
}

