package dev.wulnry.mossshop.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Config bolumunden Adventure ses calar. Deprecated Bukkit Sound enum'una
 * bagimli degildir; namespaced anahtar (orn: {@code entity.player.levelup})
 * kullanir. Hatali yapilandirmada sessizce gecer, asla istisna firlatmaz.
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    public static void play(Player player, ConfigurationSection section) {
        if (player == null || section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        String key = section.getString("sound");
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            float volume = (float) section.getDouble("volume", 1.0D);
            float pitch = (float) section.getDouble("pitch", 1.0D);
            Sound sound = Sound.sound(
                    Key.key(key.toLowerCase(Locale.ROOT)),
                    Sound.Source.MASTER,
                    volume,
                    pitch);
            player.playSound(sound);
        } catch (Exception e) {
            // Gecersiz ses anahtari islemi kesmez, ancak yapilandirma hatasinin
            // fark edilebilmesi icin loglanir (sessizce yutulmaz).
            Bukkit.getLogger().warning("[MossShopCore] Gecersiz ses anahtari '" + key + "': " + e.getMessage());
        }
    }
}
