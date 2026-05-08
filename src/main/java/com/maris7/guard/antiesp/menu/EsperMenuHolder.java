package com.maris7.guard.antiesp.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EsperMenuHolder implements InventoryHolder {
    private final int page;

    public EsperMenuHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

