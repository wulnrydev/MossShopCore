package dev.wulnry.mossshop.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault uzerinden ekonomi islemlerini soyutlar. Plugin yalnizca bu servisi
 * bilir; alttaki ekonomi eklentisi onemsizdir.
 */
public final class EconomyService {

    private Economy economy;

    /**
     * Vault ve uyumlu bir ekonomi saglayicisini baglar.
     *
     * @return baglanti basarili ise {@code true}
     */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        this.economy = provider.getProvider();
        return this.economy != null;
    }

    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Para iadesi yapar. Teslim edilemeyen urunler icin {@link
     * dev.wulnry.mossshop.shop.PurchaseService} tarafindan kullanilir.
     *
     * @return iade basarili ise {@code true}
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
}
