package dev.wulnry.mossshop.shop;

import org.bukkit.inventory.ItemStack;

/**
 * Onbelleklenmis bir market urunu. {@code template} satin alinacak itemin
 * tum verilerini (NBT, enchant, attribute, PDC...) barindiran prototiptir.
 * Hicbir veri kaybi olmamasi icin teslimde bu prototip klonlanir.
 */
public final class ShopItem {

    private final String id;
    private final int slot;
    private final double price;
    private final ItemStack template;

    public ShopItem(String id, int slot, double price, ItemStack template) {
        this.id = id;
        this.slot = slot;
        this.price = price;
        this.template = template;
    }

    public String id() {
        return id;
    }

    public int slot() {
        return slot;
    }

    public double price() {
        return price;
    }

    /**
     * Menu gosterimi/klonlama icin prototip. Disariya verilirken daima
     * {@link ItemStack#clone()} ile kopyalanmalidir.
     */
    public ItemStack template() {
        return template;
    }

    /**
     * Oyuncuya teslim edilecek tek birimlik kopya.
     */
    public ItemStack createUnit() {
        ItemStack unit = template.clone();
        unit.setAmount(1);
        return unit;
    }
}
