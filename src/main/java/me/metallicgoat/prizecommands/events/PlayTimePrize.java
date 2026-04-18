package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.event.arena.ArenaStatusChangeEvent;
import de.marcely.bedwars.api.event.arena.RoundStartEvent;
import me.metallicgoat.prizecommands.Prize;
import me.metallicgoat.prizecommands.PrizeCommandsPlugin;
import me.metallicgoat.prizecommands.config.ConfigValue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import java.util.IdentityHashMap;
import java.util.Map;

public class PlayTimePrize implements Listener {

  private final Map<Arena, BukkitTask> tasks = new IdentityHashMap<>();

  @EventHandler
  public void onRoundStart(RoundStartEvent e) {
    final Arena arena = e.getArena();

    if (ConfigValue.playTimePrizeEnabled) {
      tasks.put(arena, Bukkit.getScheduler().runTaskTimer(PrizeCommandsPlugin.getInstance(), () -> {

        for (Prize prize : ConfigValue.playTimePrizes)
          for (Player player : arena.getPlayers())
            prize.earn(arena, player, null);

      }, ConfigValue.playTimeInterval, ConfigValue.playTimeInterval));
    }
  }

  @EventHandler (priority = EventPriority.HIGHEST)
  public void onRoundEnd(ArenaStatusChangeEvent event) {
    if (event.getNewStatus() == ArenaStatus.RUNNING)
      return;

    final BukkitTask task = tasks.get(event.getArena());

    if (task != null)
      task.cancel();

    tasks.remove(event.getArena());
  }
}