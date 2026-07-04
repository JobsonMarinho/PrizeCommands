package me.metallicgoat.prizecommands.economy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Accumulates prize rewards in memory and pays them out in a single batch, instead of running a
 * command (or a database write) every time a prize is earned.
 *
 * <p>The problem this solves: dispatching {@code money give <player> <amount>} once per player every
 * few seconds forces a synchronous SQL write on the main thread for each call. With hundreds of
 * players this stalls the server. Here we sum the amounts per player and flush them once - when the
 * player leaves the arena, disconnects, the game ends, or the server shuts down.
 *
 * <p>Two kinds of rewards are batched:
 * <ul>
 *   <li>Money, paid through the Vault economy ({@link Economy#depositPlayer}).</li>
 *   <li>Arbitrary commands, summed by template and run once with the total substituted for
 *       {@code {total}} (e.g. {@code alonsolevels addexp <player> {total} true}).</li>
 * </ul>
 *
 * <p>All accumulation and flushing happens on the main thread. The outer map is still concurrent and
 * every mutation is done atomically as a defensive measure in case a prize is ever earned off-thread.
 */
public class PrizePayoutService {

  private final Logger logger;
  private final ConcurrentHashMap<UUID, PlayerPending> pending = new ConcurrentHashMap<>();

  @Nullable
  private Economy economy;

  public PrizePayoutService(Plugin plugin) {
    this.logger = plugin.getLogger();
    logInitialEconomyStatus();
  }

  // --- Economy resolution (lazy + self-healing against plugin load order) ---

  @Nullable
  private Economy getEconomy() {
    // Vault missing entirely: fall back to whatever we hooked before (may be null). The plugin
    // check must come first so Economy.class is never touched when Vault is not installed.
    if (Bukkit.getPluginManager().getPlugin("Vault") == null)
      return this.economy;

    // Always prefer the live registration: the cached instance may belong to a plugin that was
    // already disabled (e.g. the economy plugin shutting down before us during /stop). If the
    // registration is gone, keep the cached provider as a best-effort fallback.
    final RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);

    if (rsp != null)
      this.economy = rsp.getProvider();

    return this.economy;
  }

  private void logInitialEconomyStatus() {
    if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
      logger.warning("Vault was not found. [money-give] prizes will not be paid until Vault and an economy plugin are installed.");
      return;
    }

    final Economy eco = getEconomy();

    if (eco == null)
      logger.warning("Vault is present but no economy provider is registered (yet). It will be resolved lazily when a prize is paid.");
    else
      logger.info("Hooked into Vault economy: " + eco.getName());
  }

  public boolean hasEconomy() {
    return getEconomy() != null;
  }

  // --- Accumulation (main thread) ---

  public void addMoney(Player player, double amount) {
    if (amount <= 0D)
      return;

    final String name = player.getName();

    pending.compute(player.getUniqueId(), (uuid, p) -> {
      if (p == null)
        p = new PlayerPending(name);

      p.name = name;
      p.money += amount;

      return p;
    });
  }

  public void addBatchedCommand(Player player, String resolvedTemplate, double amount) {
    if (amount <= 0D)
      return;

    final String name = player.getName();

    pending.compute(player.getUniqueId(), (uuid, p) -> {
      if (p == null)
        p = new PlayerPending(name);

      p.name = name;
      p.commands.merge(resolvedTemplate, amount, Double::sum);

      return p;
    });
  }

  /** Returns the money currently accumulated (and not yet paid) for the given player. */
  public double getPendingMoney(UUID uuid) {
    final PlayerPending p = pending.get(uuid);

    return p != null ? p.money : 0D;
  }

  // --- Flush (main thread) ---

  /** Atomically removes and pays out everything owed to a single player. Safe to call repeatedly. */
  public void flush(UUID uuid) {
    final PlayerPending p = pending.remove(uuid);

    if (p != null)
      payOut(uuid, p);
  }

  /** Pays out every remaining player. Used on shutdown so nothing is lost on a graceful restart. */
  public void flushAll() {
    for (Map.Entry<UUID, PlayerPending> entry : pending.entrySet())
      payOut(entry.getKey(), entry.getValue());

    pending.clear();
  }

  private void payOut(UUID uuid, PlayerPending p) {
    // 1) Money through Vault (a single deposit instead of many command dispatches)
    if (p.money > 0D) {
      final Economy eco = getEconomy();

      if (eco == null) {
        logger.warning("Could not pay " + formatAmount(p.money) + " to " + p.name + " (" + uuid
            + "): no Vault economy is available. The money was NOT delivered.");
      } else {
        try {
          eco.depositPlayer(resolvePlayer(uuid), p.money);
        } catch (Throwable t) {
          logger.warning("Failed to deposit " + formatAmount(p.money) + " to " + p.name + " (" + uuid + "): " + t);
        }
      }
    }

    // 2) Batched commands, run once each with the accumulated total
    for (Map.Entry<String, Double> entry : p.commands.entrySet()) {
      final String command = entry.getKey().replace("{total}", formatAmount(entry.getValue()));

      try {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
      } catch (Throwable t) {
        logger.warning("Failed to run batched command '" + command + "': " + t);
      }
    }
  }

  private OfflinePlayer resolvePlayer(UUID uuid) {
    final Player online = Bukkit.getPlayer(uuid);

    return online != null ? online : Bukkit.getOfflinePlayer(uuid);
  }

  /**
   * Formats an amount for command substitution and display: whole numbers lose their decimals
   * ({@code 450.0 -> "450"}) so plugins expecting an integer don't choke, while fractional amounts
   * keep them ({@code 100.75 -> "100.75"}). Always uses a dot, never a locale-specific comma.
   */
  public static String formatAmount(double value) {
    // Defensive: should be unreachable (accumulated amounts are validated finite positives)
    if (Double.isNaN(value) || Double.isInfinite(value))
      return "0";

    // 2^53 = the exact-integer range of a double; beyond it the cast to long would misrepresent
    if (value == Math.rint(value) && Math.abs(value) <= 9007199254740992D)
      return Long.toString((long) value);

    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  /** In-memory tally of everything a single player is owed but hasn't been paid yet. */
  private static class PlayerPending {

    private volatile String name;
    private double money = 0D;
    private final Map<String, Double> commands = new LinkedHashMap<>();

    private PlayerPending(String name) {
      this.name = name;
    }
  }
}
