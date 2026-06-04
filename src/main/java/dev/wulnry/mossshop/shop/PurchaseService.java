package dev.wulnry.mossshop.shop;

import dev.wulnry.mossshop.economy.EconomyService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Satin alma is mantigini ekonomi ve envanter kontrolleriyle yurutur.
 * GUI'den bagimsizdir (SOLID - tek sorumluluk).
 *
 * <p>Tum {@code fits -> withdraw -> give} akisi oyuncu bazli bir kilit
 * altinda calisir; boylece ayni oyuncunun cift tiklamasi iki es zamanli
 * islem baslatamaz ve "para gitti, esya gelmedi" durumu olusmaz. Eger
 * para cekildikten sonra urun (beklenmedik sekilde) teslim edilemezse,
 * teslim edilemeyen miktarin bedeli oyuncuya iade edilir ve loglanir.</p>
 */
public final class PurchaseService {

    public enum Result {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        FAILED
    }

    private final Plugin plugin;
    private final EconomyService economy;

    /** Oyuncu bazli islem kilitleri (cift tiklama korumasi). */
    private final Map<UUID, Lock> playerLocks = new ConcurrentHashMap<>();

    public PurchaseService(Plugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /**
     * Satin alma adimlari: kilit -> bakiye -> envanter -> para cekme -> teslim.
     */
    public Result purchase(Player player, ShopItem item, int amount) {
        if (item == null || amount < 1) {
            return Result.FAILED;
        }

        Lock lock = playerLocks.computeIfAbsent(player.getUniqueId(), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            // Oyuncunun zaten devam eden bir islemi var (cift tiklama).
            return Result.FAILED;
        }
        try {
            double total = item.price() * amount;

            if (!economy.has(player, total)) {
                return Result.INSUFFICIENT_FUNDS;
            }

            ItemStack unit = item.createUnit();
            if (!fits(player, unit, amount)) {
                return Result.INVENTORY_FULL;
            }

            if (!economy.withdraw(player, total)) {
                return Result.FAILED;
            }

            int delivered = give(player, unit, amount);
            if (delivered < amount) {
                // Beklenmedik durum: fits() gecti ama teslim tam yapilamadi.
                // Teslim edilemeyen miktarin bedelini iade et.
                int undelivered = amount - delivered;
                double refund = item.price() * undelivered;
                economy.deposit(player, refund);
                plugin.getLogger().warning("Teslim eksik kaldi (" + player.getName() + ", urun '"
                        + item.id() + "'): " + undelivered + " adet verilemedi, " + refund + " iade edildi.");
                return delivered > 0 ? Result.SUCCESS : Result.INVENTORY_FULL;
            }
            return Result.SUCCESS;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verilen item ve miktarin oyuncu envanterine sigip sigmadigini hesaplar.
     * Custom/stacklenemeyen itemler icin guvenli (kalemli) tahmin yapar.
     */
    private boolean fits(Player player, ItemStack prototype, int amount) {
        int max = prototype.getMaxStackSize();
        int free = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                free += max;
            } else if (stack.isSimilar(prototype)) {
                free += Math.max(0, max - stack.getAmount());
            }
            if (free >= amount) {
                return true;
            }
        }
        return free >= amount;
    }

    /**
     * Urunu envantere ekler ve gercekten teslim edilen adet sayisini doner.
     * Envanter (beklenmedik sekilde) dolarsa kalan miktari yere atmaz; cagiran
     * taraf teslim edilemeyen kismin bedelini iade eder.
     *
     * @return teslim edilen toplam adet
     */
    private int give(Player player, ItemStack prototype, int amount) {
        int max = prototype.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, max);
            ItemStack stack = prototype.clone();
            stack.setAmount(give);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            int notAdded = 0;
            for (ItemStack drop : leftover.values()) {
                notAdded += drop.getAmount();
            }
            remaining -= (give - notAdded);
            if (notAdded > 0) {
                // Envanter doldu; daha fazla teslim mumkun degil.
                break;
            }
        }
        return amount - remaining;
    }

    /**
     * Oyuncu cikis yaptiginda kilidini temizler (bellek sizintisini onler).
     */
    public void clearPlayer(UUID uuid) {
        playerLocks.remove(uuid);
    }
}
