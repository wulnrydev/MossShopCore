package dev.wulnry.mossshop.command;

import dev.wulnry.mossshop.MossShopCore;
import dev.wulnry.mossshop.config.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * {@code /mossshop reload} - tum configleri ve onbellegi guvenli sekilde
 * yeniden olusturur. {@code mossshop.admin} izni gerektirir.
 */
public final class MossShopCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "mossshop.admin";

    private final MossShopCore plugin;
    private final MessageManager messages;

    public MossShopCommand(MossShopCore plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                plugin.reloadAll();
                messages.send(sender, "reload-success");
            } catch (Exception ex) {
                // Tek noktada loglama; eski onbellek korunur, eklenti acik kalir.
                plugin.getLogger().severe("Reload basarisiz: " + ex.getMessage());
                messages.send(sender, "reload-failed", "%error%", String.valueOf(ex.getMessage()));
            }
            return true;
        }
        messages.send(sender, "unknown-command");
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)
                && "reload".startsWith(args[0].toLowerCase())) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
