package me.metallicgoat.prizecommands.events;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.tools.NMSHelper;
import me.metallicgoat.prizecommands.Prize;
import me.metallicgoat.prizecommands.config.ConfigValue;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.Nullable;

public class FireballDeflectPrize implements Listener {

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntity(EntityDamageByEntityEvent event) {
    if (event.getEntityType() != EntityType.FIREBALL)
      return;

    final Entity damager = event.getDamager();
    Player player;

    if (damager.getType() == EntityType.PLAYER)
      player = (Player) damager;

    // no need to implement this for projectiles since they don't get called for this event
    else
      return;

    final Arena arena = GameAPI.get().getArenaByPlayer(player);

    if (arena == null || arena.getStatus() != ArenaStatus.RUNNING)
      return;

    for (Prize prize : ConfigValue.playerDeflectFireballPrize)
      prize.earn(arena, player, null);
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    if (NMSHelper.get().getVersion() <= 11) // not supported due to missing API
      return;
    if (!(event.getEntity().getShooter() instanceof Player))
      return;

    final Player player = (Player) event.getEntity().getShooter();
    final Arena arena = GameAPI.get().getArenaByPlayer(player);

    if (arena == null || arena.getStatus() != ArenaStatus.RUNNING)
      return;

    final Entity hit = getHitEntity(event);

    if (hit == null || hit.getType() != EntityType.FIREBALL)
      return;

    for (Prize prize : ConfigValue.playerDeflectFireballPrize)
      prize.earn(arena, player, null);
  }

  @Nullable
  private static Entity getHitEntity(ProjectileHitEvent event) {
    try {
      return (Entity) ProjectileHitEvent.class.getMethod("getHitEntity").invoke(event);
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }
}
