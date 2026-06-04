package dev.wulnry.mossshop.gui;

import dev.wulnry.mossshop.shop.ShopItem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Satin alma menusunu isaretleyen InventoryHolder. Secili urunu ve guncel
 * miktari (durum) tasir; boylece dinamik fiyat hesaplamasi tamamen bellek
 * uzerinde, gecikmesiz yapilir.
 */
public final class PurchaseHolder implements InventoryHolder {

    private final ShopItem shopItem;
    private final int minAmount;
    private final int maxAmount;
    private Inventory inventory;
    private int amount = 1;

    public PurchaseHolder(ShopItem shopItem, int maxAmount) {
        this.shopItem = shopItem;
        this.minAmount = 1;
        this.maxAmount = Math.max(1, maxAmount);
    }

    public ShopItem shopItem() {
        return shopItem;
    }

    public int amount() {
        return amount;
    }

    /**
     * Miktari verilen degisim kadar artirir/azaltir ve sinirlar arasinda tutar.
     *
     * @return miktar gercekten degistiyse {@code true}
     */
    public boolean modifyAmount(int delta) {
        int updated = Math.max(minAmount, Math.min(maxAmount, amount + delta));
        if (updated == amount) {
            return false;
        }
        amount = updated;
        return true;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
