package dev.wulnry.mossshop.gui;

import dev.wulnry.mossshop.config.ConfigManager;
import dev.wulnry.mossshop.config.MessageManager;
import dev.wulnry.mossshop.economy.EconomyService;
import dev.wulnry.mossshop.shop.PurchaseService;
import dev.wulnry.mossshop.shop.ShopItem;
import dev.wulnry.mossshop.shop.ShopManager;
import dev.wulnry.mossshop.util.ItemBuilder;
import dev.wulnry.mossshop.util.SoundUtil;
import dev.wulnry.mossshop.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tum menu yasam dongusunu yonetir: onbellek olusturma (invalidate),
 * menu acma ve olay tabanli tiklama isleme. Market acilisinda dosya okumasi
 * yapmaz; tum sablonlar bellekte hazirdir.
 */
public final class MenuManager {

    /** {@code [console]} back-button komutlari icin guvenli komut beyaz listesi. */
    private static final Set<String> ALLOWED_CONSOLE_COMMANDS = Set.of("market", "spawn", "hub");

    private final Plugin plugin;
    private final ShopManager shopManager;
    private final MessageManager messages;
    private final ConfigManager config;
    private final PurchaseService purchaseService;
    private final EconomyService economy;

    // ---- Ana market onbellegi ----
    private Component marketTitle;
    private int marketSize;
    /**
     * Ana market icerigi. Yalnizca {@link #invalidate()} sonrasi (yani
     * reloadAll) bir kez yeniden insa edilir; her {@code /market} acilisinda
     * tekrar build edilmez. {@code null} ise bir sonraki acilista lazy olarak
     * kurulur.
     */
    private ItemStack[] cachedMarketContents;

    // ---- Satin alma menusu onbellegi ----
    private String purchaseTitle;
    private int purchaseSize;
    private int productSlot;
    private int buySlot;
    private int backSlot;
    private List<String> backCommands;
    private List<String> productLore;
    private final Map<Integer, Integer> amountModifiers = new HashMap<>();
    private ItemStack[] purchaseStatic;
    private ConfigurationSection buyButtonSection;

    public MenuManager(Plugin plugin, ShopManager shopManager, MessageManager messages,
                       ConfigManager config, PurchaseService purchaseService, EconomyService economy) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.messages = messages;
        this.config = config;
        this.purchaseService = purchaseService;
        this.economy = economy;
    }

    // ============================================================
    //  ONBELLEK OLUSTURMA
    // ============================================================

    /**
     * Tum menu sablonlarini config ve urun onbelleginden yeniden olusturur.
     * reload sonrasi cagrilir.
     */
    public void invalidate() {
        // Market icerigi lazy olarak yeniden kurulur: bir sonraki openMarket
        // cagrisinda (reload sonrasi) bir kez build edilip cache'lenir.
        cachedMarketContents = null;
        buildPurchaseTemplate();
    }

    private void buildMarketTemplate() {
        ConfigurationSection market = plugin.getConfig().getConfigurationSection("market");
        this.marketTitle = Text.color(market != null ? market.getString("title", "Market") : "Market");
        this.marketSize = normalizeSize(market != null ? market.getInt("size", 54) : 54);

        ItemStack[] template = new ItemStack[marketSize];

        // Filler
        if (market != null) {
            ItemStack filler = buildFiller(market.getConfigurationSection("filler"));
            if (filler != null) {
                for (int i = 0; i < marketSize; i++) {
                    template[i] = filler.clone();
                }
            }

            // Dekorasyon itemleri
            ConfigurationSection decorations = market.getConfigurationSection("decorations");
            if (decorations != null) {
                for (String key : decorations.getKeys(false)) {
                    ConfigurationSection deco = decorations.getConfigurationSection(key);
                    if (deco == null) {
                        continue;
                    }
                    int slot = deco.getInt("slot", -1);
                    ItemStack item = ItemBuilder.fromSection(plugin, deco, Collections.emptyMap());
                    if (item != null && slot >= 0 && slot < marketSize) {
                        template[slot] = item;
                    }
                }
            }
        }

        // Urunler (statik fiyat -> onbellekte hazir gosterim)
        List<String> itemLore = market != null ? market.getStringList("item-lore") : Collections.emptyList();
        for (Map.Entry<Integer, ShopItem> entry : shopManager.bySlot().entrySet()) {
            int slot = entry.getKey();
            ShopItem shopItem = entry.getValue();
            if (slot < 0 || slot >= marketSize) {
                continue;
            }
            template[slot] = buildMarketDisplay(shopItem, itemLore);
        }

        this.cachedMarketContents = template;
    }

    private ItemStack buildMarketDisplay(ShopItem shopItem, List<String> itemLore) {
        ItemStack display = shopItem.template().clone();
        if (itemLore == null || itemLore.isEmpty()) {
            return display;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%unit-price%", config.formatPrice(shopItem.price()));
        appendLore(display, itemLore, placeholders);
        return display;
    }

    private void buildPurchaseTemplate() {
        ConfigurationSection menu = plugin.getConfig().getConfigurationSection("purchase-menu");
        if (menu == null) {
            this.purchaseStatic = new ItemStack[54];
            this.purchaseSize = 54;
            this.purchaseTitle = "Satin Al";
            this.productSlot = 22;
            this.buySlot = 31;
            this.backSlot = 49;
            this.backCommands = Collections.singletonList("market");
            this.productLore = Collections.emptyList();
            return;
        }

        this.purchaseTitle = menu.getString("title", "Satin Al");
        this.purchaseSize = normalizeSize(menu.getInt("size", 54));
        this.productSlot = menu.getInt("product-slot", 22);
        this.buySlot = menu.getInt("buy-button.slot", menu.getInt("buy-slot", 31));
        this.backSlot = menu.getInt("back-button.slot", menu.getInt("back-slot", 49));
        this.backCommands = menu.getStringList("back-button.commands");
        if (this.backCommands.isEmpty()) {
            this.backCommands = Collections.singletonList("market");
        }
        this.productLore = menu.getStringList("product-lore");
        this.buyButtonSection = menu.getConfigurationSection("buy-button");

        ItemStack[] template = new ItemStack[purchaseSize];

        // Filler
        ItemStack filler = buildFiller(menu.getConfigurationSection("filler"));
        if (filler != null) {
            for (int i = 0; i < purchaseSize; i++) {
                template[i] = filler.clone();
            }
        }

        // Miktar butonlari
        amountModifiers.clear();
        ConfigurationSection buttons = menu.getConfigurationSection("amount-buttons");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection button = buttons.getConfigurationSection(key);
                if (button == null) {
                    continue;
                }
                int slot = button.getInt("slot", -1);
                int modifier = button.getInt("modifier", 0);
                if (slot < 0 || slot >= purchaseSize || modifier == 0) {
                    continue;
                }
                ItemStack item = ItemBuilder.fromSection(plugin, button, Collections.emptyMap());
                if (item != null) {
                    template[slot] = item;
                    amountModifiers.put(slot, modifier);
                }
            }
        }

        // Geri don butonu
        ConfigurationSection back = menu.getConfigurationSection("back-button");
        if (back != null && backSlot >= 0 && backSlot < purchaseSize) {
            ItemStack backItem = ItemBuilder.fromSection(plugin, back, Collections.emptyMap());
            if (backItem != null) {
                template[backSlot] = backItem;
            }
        }

        this.purchaseStatic = template;
    }

    private ItemStack buildFiller(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return null;
        }
        ItemStack filler = ItemBuilder.fromSection(plugin, section, Collections.emptyMap());
        if (filler == null) {
            Material material = Material.matchMaterial(section.getString("material", "GRAY_STAINED_GLASS_PANE"));
            filler = new ItemStack(material != null ? material : Material.GRAY_STAINED_GLASS_PANE);
        }
        return filler;
    }

    // ============================================================
    //  MENU ACMA
    // ============================================================

    public void openMarket(Player player) {
        // Icerik yalnizca cache bossa (ilk acilis veya reload sonrasi) build edilir.
        if (cachedMarketContents == null) {
            buildMarketTemplate();
        }
        // Her oyuncuya ozel inventory instance'i zorunlu; sadece icerik cache'den
        // kopyalanir. setContents alttaki NMS katmaninda kopyaladigi icin paylasimli
        // referanslar guvenlidir (menu tiklamalari zaten iptal ediliyor).
        MarketHolder holder = new MarketHolder();
        Inventory inventory = Bukkit.createInventory(holder, marketSize, marketTitle);
        holder.setInventory(inventory);
        inventory.setContents(Arrays.copyOf(cachedMarketContents, cachedMarketContents.length));
        player.openInventory(inventory);
    }

    public void openPurchase(Player player, ShopItem item) {
        // Etkin ust sinir: config max-amount ile itemin envantere sigabilecek
        // teorik tavani (stack boyutu * 36 depo slotu) arasindaki kucuk olan.
        int effectiveMax = Math.min(config.maxAmount(), item.template().getMaxStackSize() * 36);
        PurchaseHolder holder = new PurchaseHolder(item, effectiveMax);
        Inventory inventory = Bukkit.createInventory(holder, purchaseSize, Text.color(purchaseTitle));
        holder.setInventory(inventory);
        renderPurchase(holder);
        player.openInventory(inventory);
    }

    private void renderPurchase(PurchaseHolder holder) {
        Inventory inventory = holder.getInventory();
        for (int i = 0; i < purchaseStatic.length; i++) {
            ItemStack item = purchaseStatic[i];
            inventory.setItem(i, item != null ? item.clone() : null);
        }
        if (productSlot >= 0 && productSlot < purchaseSize) {
            inventory.setItem(productSlot, buildProductDisplay(holder.shopItem(), holder.amount()));
        }
        if (buySlot >= 0 && buySlot < purchaseSize) {
            inventory.setItem(buySlot, buildBuyButton(holder.shopItem(), holder.amount()));
        }
    }

    private ItemStack buildProductDisplay(ShopItem shopItem, int amount) {
        ItemStack display = shopItem.template().clone();
        int visual = Math.max(1, Math.min(amount, display.getMaxStackSize()));
        display.setAmount(visual);
        if (productLore != null && !productLore.isEmpty()) {
            appendLore(display, productLore, purchasePlaceholders(shopItem, amount));
        }
        return display;
    }

    private ItemStack buildBuyButton(ShopItem shopItem, int amount) {
        ItemStack button = ItemBuilder.fromSection(plugin, buyButtonSection, purchasePlaceholders(shopItem, amount));
        if (button == null) {
            button = new ItemStack(Material.EMERALD_BLOCK);
        }
        return button;
    }

    private Map<String, String> purchasePlaceholders(ShopItem shopItem, int amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%unit-price%", config.formatPrice(shopItem.price()));
        placeholders.put("%total%", config.formatPrice(shopItem.price() * amount));
        return placeholders;
    }

    // ============================================================
    //  TIKLAMA ISLEME
    // ============================================================

    /**
     * Oyuncu cikis yaptiginda satin alma kilidini temizler.
     */
    public void handleQuit(UUID uuid) {
        purchaseService.clearPlayer(uuid);
    }

    public void handleMarketClick(Player player, int slot) {
        ShopItem item = shopManager.bySlot().get(slot);
        if (item == null) {
            return;
        }
        if (!canUse(player, item)) {
            messages.send(player, "no-permission");
            SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            return;
        }
        SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.click"));
        openPurchase(player, item);
    }

    /**
     * Oyuncunun belirli bir urunu satin alma iznine sahip olup olmadigi.
     * {@code mossshop.use.<item_id>} izni varsayilan olarak herkese aciktir.
     */
    private boolean canUse(Player player, ShopItem item) {
        return player.hasPermission("mossshop.use." + item.id());
    }

    public void handlePurchaseClick(Player player, PurchaseHolder holder, int slot) {
        if (slot == backSlot) {
            runBackCommands(player);
            return;
        }
        if (slot == buySlot) {
            handlePurchase(player, holder);
            return;
        }
        Integer modifier = amountModifiers.get(slot);
        if (modifier != null) {
            if (holder.modifyAmount(modifier)) {
                renderPurchase(holder);
                SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.click"));
            }
        }
    }

    private void handlePurchase(Player player, PurchaseHolder holder) {
        ShopItem item = holder.shopItem();
        if (shopManager.byId(item.id()) == null) {
            messages.send(player, "invalid-item");
            SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            player.closeInventory();
            return;
        }
        if (!canUse(player, item)) {
            messages.send(player, "no-permission");
            SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            player.closeInventory();
            return;
        }

        int amount = holder.amount();
        double total = item.price() * amount;
        PurchaseService.Result result = purchaseService.purchase(player, item, amount);

        switch (result) {
            case SUCCESS -> {
                String name = plainName(item);
                messages.send(player, "purchase-success",
                        "%amount%", String.valueOf(amount),
                        "%item%", name,
                        "%total%", config.formatPrice(total),
                        "%unit-price%", config.formatPrice(item.price()));
                SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.purchase-success"));
            }
            case INSUFFICIENT_FUNDS -> {
                messages.send(player, "insufficient-money",
                        "%total%", config.formatPrice(total),
                        "%balance%", config.formatPrice(economy.balance(player)));
                SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            }
            case INVENTORY_FULL -> {
                messages.send(player, "inventory-full");
                SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            }
            case FAILED -> {
                messages.send(player, "purchase-failed");
                SoundUtil.play(player, plugin.getConfig().getConfigurationSection("sounds.error"));
            }
        }
    }

    private void runBackCommands(Player player) {
        if (backCommands == null || backCommands.isEmpty()) {
            openMarket(player);
            return;
        }
        for (String raw : backCommands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = raw.replace("%player%", player.getName());
            if (command.toLowerCase(Locale.ROOT).startsWith("[console]")) {
                String consoleCommand = command.substring("[console]".length()).trim();
                if (!isConsoleCommandAllowed(consoleCommand)) {
                    // Guvenlik: yanlis/manipule edilmis config'in (orn. "op %player%")
                    // konsol yetkisiyle calismasini engelle.
                    plugin.getLogger().severe("Izin verilmeyen [console] back-button komutu engellendi: '"
                            + consoleCommand + "'. Izinli komutlar: " + ALLOWED_CONSOLE_COMMANDS);
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            } else if (command.toLowerCase(Locale.ROOT).startsWith("[player]")) {
                player.performCommand(command.substring("[player]".length()).trim());
            } else {
                player.performCommand(command);
            }
        }
    }

    /**
     * {@code [console]} on ekli komutlarin yalnizca onceden tanimli, zararsiz
     * gezinme komutlari olmasini saglar. Listede olmayan komutlar calismaz.
     */
    private boolean isConsoleCommandAllowed(String command) {
        if (command.isBlank()) {
            return false;
        }
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        return ALLOWED_CONSOLE_COMMANDS.contains(root);
    }

    // ============================================================
    //  YARDIMCILAR
    // ============================================================

    private void appendLore(ItemStack item, List<String> extraLore, Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore();
        List<Component> combined = new ArrayList<>();
        if (lore != null) {
            combined.addAll(lore);
        }
        for (String line : Text.apply(extraLore, placeholders)) {
            combined.add(Text.color(line));
        }
        meta.lore(combined);
        item.setItemMeta(meta);
    }

    private String plainName(ShopItem item) {
        ItemMeta meta = item.template().getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(meta.displayName());
        }
        return formatMaterial(item.template().getType());
    }

    private String formatMaterial(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private int normalizeSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        int remainder = size % 9;
        return remainder == 0 ? size : size + (9 - remainder);
    }
}
