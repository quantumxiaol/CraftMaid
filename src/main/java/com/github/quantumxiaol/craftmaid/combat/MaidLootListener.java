package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MaidLootListener implements Listener {
  private final CraftMaid plugin;

  public MaidLootListener(CraftMaid plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onEntityDeath(EntityDeathEvent event) {
    if (!plugin.isMaidEnemyExpEnabled()
        || event.getDroppedExp() > 0
        || !(event.getEntity() instanceof Enemy)
        || !wasKilledByMaid(event)) {
      return;
    }

    event.setDroppedExp(plugin.getMaidDefaultEnemyExp());
  }

  private boolean wasKilledByMaid(EntityDeathEvent event) {
    DamageSource damageSource = event.getDamageSource();
    if (damageSource != null
        && (isMaidAttacker(damageSource.getCausingEntity())
            || isMaidAttacker(damageSource.getDirectEntity()))) {
      return true;
    }

    if (event.getEntity().getLastDamageCause()
        instanceof EntityDamageByEntityEvent damageByEntityEvent) {
      return isMaidAttacker(damageByEntityEvent.getDamager());
    }
    return false;
  }

  private boolean isMaidAttacker(Entity entity) {
    if (entity == null) {
      return false;
    }
    if (plugin.getMaidNpcService().isMaidEntity(entity)) {
      return true;
    }
    if (!(entity instanceof Projectile projectile)) {
      return false;
    }

    ProjectileSource shooter = projectile.getShooter();
    return shooter instanceof Entity shooterEntity
        && plugin.getMaidNpcService().isMaidEntity(shooterEntity);
  }
}
