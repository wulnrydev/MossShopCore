package dev.wulnry.mossshop.config;

import dev.wulnry.mossshop.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * messages.yml dosyasini yukler ve onbellekte tutar. Tum mesajlar
 * Adventure API ile gonderilir ve {@code %prefix%} dahil placeholder destekler.
 */
public final class MessageManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private FileConfiguration messages;
    /** prefix her mesajda yeniden cozulmesin diye reload'da bir kez onbelleklenir. */
    private String cachedPrefix = "";

    public MessageManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        // ConfigManager.reload() bu cagridan once calistigindan prefix gunceldir.
        this.cachedPrefix = configManager.prefix();
    }

    /**
     * Verilen yola ait mesaji placeholderlari uygulayarak gonderir.
     *
     * @param pairs placeholder/deger ciftleri (orn: "%amount%", "5")
     */
    public void send(CommandSender target, String path, String... pairs) {
        String raw = messages.getString(path);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        // Ara HashMap olusturmadan dogrudan string degisimi yapilir; saniyede
        // yuzlerce mesaj gonderilen ortamda gereksiz GC baskisi onlenir.
        // prefix reload'da onbelleklenir, her mesajda yeniden resolve edilmez.
        String message = raw.replace("%prefix%", cachedPrefix);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            message = message.replace(pairs[i], pairs[i + 1]);
        }
        Component component = Text.color(message);
        target.sendMessage(component);
    }
}
