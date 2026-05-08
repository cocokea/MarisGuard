package com.maris7.guard.antiesp.menu;

import com.maris7.guard.antiesp.esper.AbstractEsperManager;
import com.maris7.guard.antiesp.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public final class EsperMenu {
    public static final int PAGE_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final String HOLDER_PREFIX = "esper-menu:";

    private EsperMenu() {
    }

    public static void open(Player viewer, AbstractEsperManager manager, int page) {
        List<AbstractEsperManager.FlaggedPlayerEntry> flagged = manager.getFlaggedPlayers();
        int maxPage = Math.max(0, (flagged.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(new EsperMenuHolder(safePage), 54,
                ColorUtil.colorize("&8Suspects " + manager.getFlaggedPlayerCount()));

        int start = safePage * PAGE_SIZE;
        int end = Math.min(flagged.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            AbstractEsperManager.FlaggedPlayerEntry entry = flagged.get(index);
            inventory.setItem(index - start, createPlayerHead(entry));
        }

        inventory.setItem(PREVIOUS_SLOT, createArrow("&#00f986Next page", List.of("&fClick to continue")));
        inventory.setItem(NEXT_SLOT, createArrow("&#00f986Next page", List.of("&fClick to continue")));
        viewer.openInventory(inventory);
    }

    private static ItemStack createPlayerHead(AbstractEsperManager.FlaggedPlayerEntry entry) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.uniqueId());
        meta.setOwningPlayer(offlinePlayer);
        meta.setDisplayName(ColorUtil.colorize("&f" + entry.name()));
        meta.setLore(List.of(
                ColorUtil.colorize("&7check: &fesper"),
                ColorUtil.colorize("&7flags: &f" + entry.violations())
        ));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack createArrow(String name, List<String> loreLines) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ColorUtil.colorize(name));
        meta.setLore(loreLines.stream().map(ColorUtil::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }
}

