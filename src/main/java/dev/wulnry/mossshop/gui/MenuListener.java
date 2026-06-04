package dev.wulnry.mossshop.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Menu etkilesimlerini tamamen olay tabanli isler. Hicbir scheduler/task
 * kullanmaz. Eklenti menulerindeki tum tiklama ve surukleme islemleri iptal
 * edilerek item hirsizligi ve istenmeyen tasima engellenir.
 *
 * <p>Olaylar {@link EventPriority#HIGH} ve {@code ignoreCancelled = true} ile
 * dinlenir; boylece bir AntiCheat/koruma eklentisi olayi zaten iptal etmisse
 * MenuListener tekrar islem yapmaz (hayalet tiklama / cift satin alma onlenir).</p>
 */
public final class MenuListener implements Listener {

    private final MenuManager menuManager;

    public MenuListener(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MarketHolder) && !(holder instanceof PurchaseHolder)) {
            return;
        }

        // Eklenti menusu acikken her turlu tiklamayi (shift-click dahil) iptal et.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Sadece ust (menu) envanterine yapilan tiklamalar islenir.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (holder instanceof MarketHolder) {
            menuManager.handleMarketClick(player, slot);
        } else if (holder instanceof PurchaseHolder purchaseHolder) {
            menuManager.handlePurchaseClick(player, purchaseHolder, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MarketHolder || holder instanceof PurchaseHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menuManager.handleQuit(event.getPlayer().getUniqueId());
    }
}
