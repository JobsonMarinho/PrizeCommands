package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.event.player.PlayerQuitArenaEvent;
import me.metallicgoat.prizecommands.PrizeCommandsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Pays out a player's accumulated (batched) prizes when they leave the arena.
 *
 * <p>{@link PlayerQuitArenaEvent} fires for every way a player can leave a match - {@code LEAVE},
 * {@code SERVER_DISCONNECT}, {@code GAME_END}, elimination, etc. - so a single handler covers "left
 * the arena", "disconnected" and "the game ended" at once. The flush is idempotent, so if the event
 * fires more than once for the same player it simply finds nothing left to pay.
 */
public class EconomyPayoutListener implements Listener {

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuitArena(PlayerQuitArenaEvent event) {
    final Player player = event.getPlayer();

    PrizeCommandsPlugin.getInstance().getPayoutService().flush(player.getUniqueId());
  }
}
