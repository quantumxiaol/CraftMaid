package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class MaidDamagePolicyListener implements Listener {
  private static final long OWNER_ATTACK_MESSAGE_COOLDOWN_MILLIS = 3000L;

  private final CraftMaid plugin;
  private final Map<UUID, Long> nextOwnerAttackMessageAt = new HashMap<>();

  public MaidDamagePolicyListener(CraftMaid plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (!plugin.getMaidSelfDefenseService().isActiveTarget(player)) {
      plugin.getMaidNpcService().removeSelfDefenseTarget(player.getUniqueId(), player.getName());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    plugin.getMaidSelfDefenseService().forgive(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onEntityDamage(EntityDamageByEntityEvent event) {
    Entity victim = event.getEntity();
    Entity attacker = resolveAttacker(event.getDamager());

    if (plugin.getMaidNpcService().isMaidEntity(victim) && attacker instanceof Player player) {
      handleMaidDamagedByPlayer(event, player);
      return;
    }

    if (plugin.getMaidNpcService().isMaidEntity(attacker) && victim instanceof Player player) {
      handlePlayerDamagedByMaid(event, player);
    }
  }

  private void handleMaidDamagedByPlayer(EntityDamageByEntityEvent event, Player attacker) {
    if (isMaster(attacker)) {
      handleMasterDamagedMaid(event, attacker);
      return;
    }

    CraftMaidConfig.SelfDefenseSettings settings = plugin.getMaidCombatSettings().selfDefense();
    if (settings == null || !settings.enabled() || !settings.targetPlayers()) {
      return;
    }

    plugin.getMaidSelfDefenseService().startDefense(attacker);
  }

  private void handleMasterDamagedMaid(EntityDamageByEntityEvent event, Player master) {
    String policy = plugin.getMaidCombatSettings().ownerDamagePolicy();
    if (!"allow_and_retaliate".equals(policy)) {
      plugin.getMaidSelfDefenseService().forgive(master);
    }

    if ("cancel".equals(policy)) {
      event.setCancelled(true);
      recordOwnerAttack(master);
      return;
    }

    if ("allow_no_retaliate".equals(policy)) {
      recordOwnerAttack(master);
      return;
    }

    CraftMaidConfig.SelfDefenseSettings settings = plugin.getMaidCombatSettings().selfDefense();
    if (settings != null && settings.enabled() && settings.targetMaster()) {
      plugin.getMaidSelfDefenseService().startDefense(master);
    }
  }

  private void handlePlayerDamagedByMaid(EntityDamageByEntityEvent event, Player victim) {
    if (isMaster(victim)) {
      event.setCancelled(true);
      plugin.getMaidSelfDefenseService().forgive(victim);
      return;
    }

    if (!plugin.getMaidSelfDefenseService().isActiveTarget(victim)) {
      event.setCancelled(true);
      plugin.getMaidSelfDefenseService().forgive(victim);
    }
  }

  private void recordOwnerAttack(Player master) {
    plugin.getJobEventBuffer().add("主人误伤了女仆，女仆没有还手。");
    if (!plugin.getMaidCombatSettings().ownerAttackMessage()) {
      return;
    }

    long now = System.currentTimeMillis();
    long nextMessageAt = nextOwnerAttackMessageAt.getOrDefault(master.getUniqueId(), 0L);
    if (now < nextMessageAt) {
      return;
    }

    nextOwnerAttackMessageAt.put(master.getUniqueId(), now + OWNER_ATTACK_MESSAGE_COOLDOWN_MILLIS);
    master.sendMessage(
        Component.text(plugin.getMaidName() + " 轻轻躲开了这一下，没有还手。", NamedTextColor.LIGHT_PURPLE));
  }

  private boolean isMaster(Player player) {
    return player.getName().equalsIgnoreCase(plugin.getMasterName());
  }

  private Entity resolveAttacker(Entity damager) {
    if (!(damager instanceof Projectile projectile)) {
      return damager;
    }

    ProjectileSource shooter = projectile.getShooter();
    return shooter instanceof Entity shooterEntity ? shooterEntity : damager;
  }
}
