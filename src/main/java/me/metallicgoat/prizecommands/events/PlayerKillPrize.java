package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.event.player.PlayerKillPlayerEvent;
import de.marcely.bedwars.tools.Helper;
import me.metallicgoat.prizecommands.Prize;
import me.metallicgoat.prizecommands.config.ConfigValue;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;

public class PlayerKillPrize implements Listener {

  @EventHandler
  public void onFinalKill(PlayerKillPlayerEvent event) {
    if (!event.isCountingKillStats())
      return;

    final Player victim = event.getPlayer();
    final Player killer = event.getKiller();
    final Arena arena = event.getArena();

    if (arena != null && killer != null) {
      final Location killerLoc = killer.getLocation();
      final Team team = arena.getPlayerTeam(victim);
      final Team killerTeam = arena.getPlayerTeam(killer);
      final List<Prize> prizes = arena.isBedDestroyed(team) ? ConfigValue.playerFinalKillPrize : ConfigValue.playerKillPrize;
      final HashMap<String, String> placeholderReplacements = new HashMap<>();

      if (killerTeam != null) {
        placeholderReplacements.put("killer-team-name", killerTeam.getDisplayName());
        placeholderReplacements.put("killer-team-color", killerTeam.getDisplayName());
        placeholderReplacements.put("killer-team-color-code", killerTeam.getBungeeChatColor().toString());
      }

      placeholderReplacements.put("killer-real-name", killer.getName());
      placeholderReplacements.put("killer-display-name", Helper.get().getPlayerDisplayName(killer));
      placeholderReplacements.put("killer-X", String.valueOf(killerLoc.getX()));
      placeholderReplacements.put("killer-y", String.valueOf(killerLoc.getY()));
      placeholderReplacements.put("killer-z", String.valueOf(killerLoc.getZ()));

      for (Prize prize : prizes)
        prize.earn(arena, killer, placeholderReplacements);

    }
  }
}
