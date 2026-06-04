package dev.wulnry.mossshop.shop;

import dev.wulnry.mossshop.config.ConfigManager;
import dev.wulnry.mossshop.util.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tum market urunlerini config'den okuyup onbellekte tutar. Veritabani
 * kullanilmaz; reload sonrasi onbellek guvenli sekilde yeniden olusturulur.
 */
public final class ShopManager {

    private final Plugin plugin;
    private final ConfigManager configManager;

    /** Urun kimligi -> urun. */
    private final Map<String, ShopItem> items = new LinkedHashMap<>();
    /** Ana menu slotu -> urun (hizli tiklama cozumlemesi icin). */
    private final Map<Integer, ShopItem> bySlot = new HashMap<>();

    public ShopManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void reload() {
        // Onbellek yerel haritalara insa edilir ve yalnizca basariyla
        // tamamlandiginda atomik olarak degistirilir; yukleme ortasinda hata
        // olusursa mevcut onbellek bozulmadan korunur (rollback).
        Map<String, ShopItem> newItems = new LinkedHashMap<>();
        Map<Integer, ShopItem> newBySlot = new HashMap<>();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("items");
        if (root == null) {
            plugin.getLogger().warning("config.yml icinde 'items' bolumu bulunamadi.");
            swap(newItems, newBySlot);
            return;
        }

        int menuSize = Math.max(9, plugin.getConfig().getInt("market.size", 54));

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                double price = section.getDouble("price", 0.0D);
                int slot = section.getInt("slot", -1);

                // Slot cakismasi: ikinci urunu sessizce ustune yazmak yerine
                // hatayi severe olarak logla ve urunu HIC yukleme.
                if (slot >= 0 && slot < menuSize && newBySlot.containsKey(slot)) {
                    plugin.getLogger().severe("Cakisan slot " + slot + "; urun '" + id
                            + "' YUKLENMEDI (slot zaten '" + newBySlot.get(slot).id() + "' tarafindan kullaniliyor).");
                    continue;
                }

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%price%", configManager.formatPrice(price));
                placeholders.put("%unit-price%", configManager.formatPrice(price));

                ItemStack template = ItemBuilder.fromSection(plugin, section, placeholders);
                if (template == null) {
                    plugin.getLogger().warning("Urun olusturulamadi, atlaniyor: " + id);
                    continue;
                }

                ShopItem item = new ShopItem(id, slot, price, template);
                newItems.put(id, item);

                if (slot >= 0 && slot < menuSize) {
                    newBySlot.put(slot, item);
                } else {
                    plugin.getLogger().warning("Urun '" + id + "' icin slot (" + slot + ") menu boyutu disinda; menude gosterilmeyecek.");
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Urun yuklenirken hata (" + id + "): " + ex.getMessage());
            }
        }

        swap(newItems, newBySlot);
        plugin.getLogger().info(items.size() + " market urunu onbellege alindi.");
    }

    private void swap(Map<String, ShopItem> newItems, Map<Integer, ShopItem> newBySlot) {
        items.clear();
        items.putAll(newItems);
        bySlot.clear();
        bySlot.putAll(newBySlot);
    }

    public Map<Integer, ShopItem> bySlot() {
        return Collections.unmodifiableMap(bySlot);
    }

    public ShopItem byId(String id) {
        return items.get(id);
    }

    public int size() {
        return items.size();
    }
}
