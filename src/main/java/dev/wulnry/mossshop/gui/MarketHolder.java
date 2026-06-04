package dev.wulnry.mossshop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Ana market menusunu isaretleyen InventoryHolder. Olay tabanli tiklama
 * cozumlemesi icin menuyu tanimlar; durum (slot->urun) {@link dev.wulnry.mossshop.shop.ShopManager}
 * uzerinden cozulur.
 */
public final class MarketHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
