package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MaidCombatFightbackListener implements Listener {
  private final CraftMaid plugin;

  public MaidCombatFightbackListener(CraftMaid plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMasterDamaged(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player player)
        || !plugin.getMaidNpcService().isGuarding()
        || !plugin.getMaidCombatSettings().guardFightback().enabled()
        || !player.getName().equalsIgnoreCase(plugin.getMasterName())) {
      return;
    }

    Entity attacker = resolveAttacker(event.getDamager());
    MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
    if (policy == null || !policy.isFightbackTarget(attacker)) {
      return;
    }

    plugin.getMaidNpcService().markGuardFightbackTarget(attacker);
  }

  private Entity resolveAttacker(Entity damager) {
    if (!(damager instanceof Projectile projectile)) {
      return damager;
    }

    ProjectileSource shooter = projectile.getShooter();
    return shooter instanceof Entity shooterEntity ? shooterEntity : damager;
  }
}
