package me.metallicgoat.prizecommands;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.arena.picker.ArenaPickerAPI;
import de.marcely.bedwars.api.arena.picker.condition.ArenaConditionGroup;
import de.marcely.bedwars.api.exception.ArenaConditionParseException;
import de.marcely.bedwars.api.message.Message;
import de.marcely.bedwars.tools.Helper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import me.metallicgoat.prizecommands.config.ConfigValue;
import me.metallicgoat.prizecommands.economy.PrizePayoutService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


@AllArgsConstructor
public class Prize {

  // Special "commands" that are batched instead of dispatched. See earnUnsafe() / handleBatch().
  private static final String MONEY_PREFIX = "[money-give]";
  private static final String BATCH_PREFIX = "[batch:";

  public final String prizeId;
  public final String permission;
  public final List<String> commands;
  public final List<String> playerCommands;
  public final List<String> broadcast;
  public final List<String> privateMessage;
  public final List<String> supportedArenasNames;
  public final boolean enabled;

  public void earn(Arena arena, Player player, Map<String, String> placeholderReplacements) {
    if (Bukkit.isPrimaryThread()) {
      earnUnsafe(arena, player, placeholderReplacements);
    } else {
      Bukkit.getScheduler().runTask(PrizeCommandsPlugin.getInstance(), () ->
          earnUnsafe(arena, player, placeholderReplacements)
      );
    }
  }

  private void earnUnsafe(Arena arena, Player player, Map<String, String> placeholderReplacements) {
    if (!ConfigValue.enabled)
      return;

    // Only run prize for supported arenas (or all arenas if empty list)
    if (!isArenaSupported(arena))
      return;

    if (this.permission != null
        && !this.permission.isEmpty()
        && !player.hasPermission(permission))
      return;

    final PrizePayoutService payout = PrizeCommandsPlugin.getInstance().getPayoutService();
    double moneyEarned = 0D;

    if (commands != null) {
      for (String cmd : commands) {
        if (cmd == null || cmd.trim().isEmpty())
          continue;

        // [money-give] <amount> -> accumulate and pay through Vault when the player leaves
        if (isToken(cmd, MONEY_PREFIX)) {
          final Double amount = parseMoneyAmount(cmd);

          if (amount != null) {
            payout.addMoney(player, amount);
            moneyEarned += amount;
          } else {
            warnBadToken(cmd, "invalid or missing amount");
          }

          continue;
        }

        // [batch:<amount>] <command with {total}> -> sum and run once on leave
        if (isToken(cmd, BATCH_PREFIX)) {
          handleBatch(cmd, player, payout);
          continue;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatString(player, arena, cmd, placeholderReplacements));
      }
    }

    if (playerCommands != null) {
      for (String cmd : playerCommands)
        player.performCommand(formatString(player, arena, cmd, placeholderReplacements));
    }

    // Messages get two extra placeholders so the config can tell the player what they earned,
    // even though the money is only handed over later, in one batch.
    if (broadcast != null || privateMessage != null) {
      final Map<String, String> msgReplacements =
          placeholderReplacements != null ? new HashMap<>(placeholderReplacements) : new HashMap<>();

      msgReplacements.put("money-earned", PrizePayoutService.formatAmount(moneyEarned));
      msgReplacements.put("money-pending", PrizePayoutService.formatAmount(payout.getPendingMoney(player.getUniqueId())));

      if (broadcast != null) {
        for (String msg : broadcast)
          arena.broadcast(formatMessage(player, arena, msg, msgReplacements));
      }

      if (privateMessage != null) {
        for (String msg : privateMessage)
          player.sendMessage(formatString(player, arena, msg, msgReplacements));
      }
    }
  }

  // Returns true if cmd (ignoring surrounding whitespace) starts with the given token prefix.
  private static boolean isToken(String cmd, String prefix) {
    final String trimmed = cmd.trim();

    return trimmed.length() >= prefix.length()
        && trimmed.substring(0, prefix.length()).equalsIgnoreCase(prefix);
  }

  // Parses the amount out of "[money-give] <amount>", tolerating an optional player token in
  // between (e.g. "[money-give] {player-real-name} 50") by taking the last whitespace token.
  private static Double parseMoneyAmount(String cmd) {
    final String rest = cmd.trim().substring(MONEY_PREFIX.length()).trim();

    if (rest.isEmpty())
      return null;

    final String[] parts = rest.split("\\s+");

    return parsePositiveDouble(parts[parts.length - 1]);
  }

  // Parses "[batch:<amount>] <command template>" and queues it. The template must contain {total},
  // which is replaced by the accumulated sum when the batch is finally run.
  private void handleBatch(String cmd, Player player, PrizePayoutService payout) {
    final String trimmed = cmd.trim();
    final int close = trimmed.indexOf(']');

    if (close < 0) {
      warnBadToken(cmd, "missing ']'");
      return;
    }

    final Double amount = parsePositiveDouble(trimmed.substring(BATCH_PREFIX.length(), close).trim());

    if (amount == null) {
      warnBadToken(cmd, "invalid or missing amount");
      return;
    }

    final String template = trimmed.substring(close + 1).trim();

    if (template.isEmpty()) {
      warnBadToken(cmd, "missing command template");
      return;
    }

    if (!template.contains("{total}")) {
      warnBadToken(cmd, "template is missing the {total} placeholder");
      return;
    }

    payout.addBatchedCommand(player, resolvePlayerPlaceholders(template, player), amount);
  }

  private static Double parsePositiveDouble(String s) {
    try {
      final double value = Double.parseDouble(s);

      if (value <= 0D || Double.isNaN(value) || Double.isInfinite(value))
        return null;

      return value;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  // Resolves only player-scoped placeholders (the player is the same at pay-out time; the arena may
  // not be). {total} is intentionally left untouched - it is filled in when the batch runs.
  private static String resolvePlayerPlaceholders(String s, Player player) {
    return s
        .replace("{player-real-name}", player.getName())
        .replace("{player-display-name}", Helper.get().getPlayerDisplayName(player))
        .replace("{player-uuid}", player.getUniqueId().toString());
  }

  private void warnBadToken(String cmd, String reason) {
    PrizeCommandsPlugin.getInstance().getLogger()
        .warning("Prize '" + this.prizeId + "': ignoring malformed token '" + cmd + "' (" + reason + ")");
  }

  private String formatString(Player player, Arena arena, String string, Map<String, String> placeholderReplacements) {
    return formatMessage(player, arena, string, placeholderReplacements).done();
  }


  private Message formatMessage(Player player, Arena arena, String string, Map<String, String> placeholderReplacements) {
    final Message formattedString = Message.build(string);

    // Placeholder values (Supported by EVERY prize)
    final Team team = arena.getPlayerTeam(player);
    final String teamName = team != null ? team.getDisplayName() : "";
    final String teamColor = team != null ? team.name() : "";
    final String teamColorCode = team != null ? team.getBungeeChatColor().toString() : "";
    final String arenaName = arena.getDisplayName();
    final String arenaWorld = arena.getGameWorld() != null ? arena.getGameWorld().getName() : "";
    final String playerRealName = player.getName();
    final String playerDisplayName = Helper.get().getPlayerDisplayName(player);
    final String playerX = String.valueOf(player.getLocation().getX());
    final String playerY = String.valueOf(player.getLocation().getY());
    final String playerZ = String.valueOf(player.getLocation().getZ());

    formattedString
        .placeholder("team-name", teamName)
        .placeholder("team-color", teamColor)
        .placeholder("team-color-code", teamColorCode)
        .placeholder("arena-name", arenaName)
        .placeholder("arena-world", arenaWorld)
        .placeholder("player-real-name", playerRealName)
        .placeholder("player-display-name", playerDisplayName)
        .placeholder("player-x", playerX)
        .placeholder("player-y", playerY)
        .placeholder("player-z", playerZ);

    // Translate event specific placeholders
    if (placeholderReplacements != null) {
      for (Map.Entry<String, String> stringSet : placeholderReplacements.entrySet())
        formattedString.placeholder(stringSet.getKey(), stringSet.getValue());
    }

    return formattedString;
  }

  public boolean isArenaSupported(Arena arena) {
    if (this.supportedArenasNames == null)
      return true;

    for (String arenaName : this.supportedArenasNames) {
      try {
        final ArenaConditionGroup group = ArenaPickerAPI.get().parseCondition(arenaName);

        if (group.check(arena))
          return true;

      } catch (ArenaConditionParseException ignored) {
        // Guess it's not a condition <Shrug Emoji>

        // Just by name?
        if (arena.getName().equalsIgnoreCase(arenaName)) {
          return true;
        }
      }
    }

    return false;
  }
}
