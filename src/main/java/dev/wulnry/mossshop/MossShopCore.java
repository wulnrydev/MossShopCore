package dev.wulnry.mossshop;

import dev.wulnry.mossshop.command.MarketCommand;
import dev.wulnry.mossshop.command.MossShopCommand;
import dev.wulnry.mossshop.config.ConfigManager;
import dev.wulnry.mossshop.config.MessageManager;
import dev.wulnry.mossshop.economy.EconomyService;
import dev.wulnry.mossshop.gui.MenuListener;
import dev.wulnry.mossshop.gui.MenuManager;
import dev.wulnry.mossshop.shop.PurchaseService;
import dev.wulnry.mossshop.shop.ShopManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MossShopCore giris noktasi. Tum servisleri kurar, onbellegi olusturur ve
 * olay/komut kayitlarini yapar. Performans odaklidir: hicbir surekli task ya
 * da scheduler kullanmaz, tum veriler baslangicta onbellege alinir.
 */
public final class MossShopCore extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private ShopManager shopManager;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        // Vault zorunlu bagimlilik.
        EconomyService economyService = new EconomyService();
        if (!economyService.setup()) {
            getLogger().severe("Vault veya uyumlu bir ekonomi eklentisi bulunamadi! MossShopCore devre disi birakiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this, configManager);
        this.shopManager = new ShopManager(this, configManager);

        // Baslangic onbellegi. Ilk yukleme basarisiz olursa eklenti devre disi kalir.
        try {
            reloadAll();
        } catch (Exception ex) {
            getLogger().severe("Baslangic yapilandirmasi yuklenemedi, MossShopCore devre disi: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PurchaseService purchaseService = new PurchaseService(this, economyService);
        this.menuManager = new MenuManager(this, shopManager, messageManager, configManager, purchaseService, economyService);
        this.menuManager.invalidate();

        getServer().getPluginManager().registerEvents(new MenuListener(menuManager), this);

        registerCommand("market", new MarketCommand(menuManager, messageManager));
        MossShopCommand adminCommand = new MossShopCommand(this, messageManager);
        PluginCommand mossshop = getCommand("mossshop");
        if (mossshop != null) {
            mossshop.setExecutor(adminCommand);
            mossshop.setTabCompleter(adminCommand);
        }

        getLogger().info("MossShopCore etkinlestirildi. (" + shopManager.size() + " urun)");
    }

    /**
     * Tum configleri ve onbellegi yeniden olusturur. Hata olusursa istisna
     * firlatilir; loglama ve kullaniciya bildirim tek bir yerde (cagiran)
     * yapilir. {@link ShopManager} onbellegini atomik olarak degistirdiginden,
     * yukleme ortasinda hata olsa bile eski urun onbellegi bozulmaz (rollback).
     */
    public void reloadAll() {
        configManager.reload();
        messageManager.reload();
        shopManager.reload();
        if (menuManager != null) {
            menuManager.invalidate();
        }
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().warning("plugin.yml icinde '" + name + "' komutu tanimli degil.");
        }
    }
}
