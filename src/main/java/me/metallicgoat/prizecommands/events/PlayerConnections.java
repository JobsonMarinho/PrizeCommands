package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.event.player.PlayerJoinArenaEvent;
import de.marcely.bedwars.api.event.player.PlayerQuitArenaEvent;
import de.marcely.bedwars.api.event.player.PlayerRejoinArenaEvent;
import me.metallicgoat.prizecommands.Prize;
import me.metallicgoat.prizecommands.config.ConfigValue;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerConnections implements Listener {

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoinArena(PlayerJoinArenaEvent event) {
    if (event.hasIssues())
      return;

    final Arena arena = event.getArena();
    final Player player = event.getPlayer();

    for (Prize prize : ConfigValue.playerJoinArenaPrize)
      prize.earn(arena, player, null);
  }

  @EventHandler
  public void onPlayerLeaveArena(PlayerQuitArenaEvent event) {
    final Arena arena = event.getArena();
    final Player player = event.getPlayer();

    for (Prize prize : ConfigValue.playerLeaveArenaPrize)
      prize.earn(arena, player, null);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerRejoinArena(PlayerRejoinArenaEvent event) {
    if (event.hasIssues())
      return;

    final Arena arena = event.getArena();
    final Player player = event.getPlayer();

    for (Prize prize : ConfigValue.playerRejoinArenaPrize)
      prize.earn(arena, player, null);
  }
}
