package dev.wulnry.mossshop.config;

import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * config.yml uzerindeki genel ayarlari (prefix, fiyat formati, limitler)
 * onbellekte tutar. Market acilisinda dosya okumasi yapilmaz; tum degerler
 * burada bir kez cozulup saklanir.
 */
public final class ConfigManager {

    private final Plugin plugin;

    private volatile String prefix;
    private volatile int maxAmount;

    /**
     * {@link DecimalFormat} thread-safe degildir. +400 oyunculu ortamda
     * {@link #formatPrice(double)} es zamanli cagrildiginda bozuk format veya
     * istisna olusmamasi icin her thread'e ozel bir ornek tutulur. reload
     * sirasinda ThreadLocal yeniden olusturulur; boylece tum thread'ler guncel
     * pattern'i alir.
     */
    private volatile ThreadLocal<DecimalFormat> priceFormat;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        this.prefix = plugin.getConfig().getString("prefix", "");
        this.maxAmount = Math.max(1, plugin.getConfig().getInt("max-amount", 2304));
        final String pattern = plugin.getConfig().getString("price-format", "#,##0.##");
        this.priceFormat = ThreadLocal.withInitial(
                () -> new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT)));
    }

    public String prefix() {
        return prefix;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public String formatPrice(double value) {
        return priceFormat.get().format(value);
    }
}
