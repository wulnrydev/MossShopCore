package dev.wulnry.mossshop.command;

import dev.wulnry.mossshop.config.MessageManager;
import dev.wulnry.mossshop.gui.MenuManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /market} - oyuncuya dogrudan ana market menusunu acar.
 * {@code mossshop.use} izni gerektirir (varsayilan: herkese acik).
 */
public final class MarketCommand implements CommandExecutor {

    private static final String USE_PERMISSION = "mossshop.use";

    private final MenuManager menuManager;
    private final MessageManager messages;

    public MarketCommand(MenuManager menuManager, MessageManager messages) {
        this.menuManager = menuManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            messages.send(player, "no-permission");
            return true;
        }
        menuManager.openMarket(player);
        return true;
    }
}
