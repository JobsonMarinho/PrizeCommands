package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.event.arena.ArenaBedBreakEvent;
import de.marcely.bedwars.api.event.arena.ArenaBedBreakEvent.Result;
import me.metallicgoat.prizecommands.Prize;
import me.metallicgoat.prizecommands.config.ConfigValue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;

public class PlayerBreakBedPrize implements Listener {

  @EventHandler(priority = EventPriority.MONITOR)
  public void onBedDestroy(ArenaBedBreakEvent e) {
    if (!e.isPlayerCaused() || e.getResult() == Result.CANCEL)
      return;

    final HashMap<String, String> placeholderReplacements = new HashMap<>();
    final Player player = e.getPlayer();
    final Arena arena = e.getArena();
    final Team team = e.getTeam();

    placeholderReplacements.put("destroyed-team-name", team.getDisplayName());
    placeholderReplacements.put("destroyed-team-color", team.name());
    placeholderReplacements.put("destroyed-team-color-code", team.getBungeeChatColor().toString());

    for (Prize prize : ConfigValue.playerBreakBreakBedPrize)
      prize.earn(arena, player, placeholderReplacements);
  }
}