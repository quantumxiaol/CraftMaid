package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

public final class MaidDamagePolicyListener implements Listener {
  private static final long OWNER_ATTACK_MESSAGE_COOLDOWN_MILLIS = 3000L;

  private final CraftMaid plugin;
  private final Map<UUID, Long> selfDefenseTargets = new HashMap<>();
  private final Map<UUID, BukkitTask> selfDefenseCleanupTasks = new HashMap<>();
  private final Map<UUID, Long> nextOwnerAttackMessageAt = new HashMap<>();

  public MaidDamagePolicyListener(CraftMaid plugin) {
    this.plugin = plugin;
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

    registerSelfDefenseTarget(attacker, settings);
  }

  private void handleMasterDamagedMaid(EntityDamageByEntityEvent event, Player master) {
    String policy = plugin.getMaidCombatSettings().ownerDamagePolicy();
    if (!"allow_and_retaliate".equals(policy)) {
      plugin.getMaidNpcService().forgiveCombatTarget(master);
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
      registerSelfDefenseTarget(master, settings);
    }
  }

  private void handlePlayerDamagedByMaid(EntityDamageByEntityEvent event, Player victim) {
    if (isMaster(victim)) {
      event.setCancelled(true);
      plugin.getMaidNpcService().forgiveCombatTarget(victim);
      return;
    }

    if (!isActiveSelfDefenseTarget(victim)) {
      event.setCancelled(true);
      plugin.getMaidNpcService().forgiveCombatTarget(victim);
    }
  }

  private void registerSelfDefenseTarget(
      Player attacker, CraftMaidConfig.SelfDefenseSettings settings) {
    long expiresAt = System.currentTimeMillis() + settings.durationSeconds() * 1000L;
    selfDefenseTargets.put(attacker.getUniqueId(), expiresAt);

    BukkitTask existingTask = selfDefenseCleanupTasks.remove(attacker.getUniqueId());
    if (existingTask != null) {
      existingTask.cancel();
    }
    BukkitTask cleanupTask =
        Bukkit.getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  selfDefenseTargets.remove(attacker.getUniqueId());
                  selfDefenseCleanupTasks.remove(attacker.getUniqueId());
                },
                settings.durationSeconds() * 20L);
    selfDefenseCleanupTasks.put(attacker.getUniqueId(), cleanupTask);

    plugin.getMaidNpcService().markSelfDefenseTarget(attacker, settings.durationSeconds());
  }

  private boolean isActiveSelfDefenseTarget(Player player) {
    Long expiresAt = selfDefenseTargets.get(player.getUniqueId());
    if (expiresAt == null) {
      return false;
    }

    long now = System.currentTimeMillis();
    if (now >= expiresAt) {
      clearSelfDefenseTarget(player);
      return false;
    }

    CraftMaidConfig.SelfDefenseSettings settings = plugin.getMaidCombatSettings().selfDefense();
    if (settings == null || !settings.forgiveWhenAttackerFar()) {
      return true;
    }

    Location maidLocation =
        plugin.getMaidNpcService().getMaidLivingEntity() == null
            ? null
            : plugin.getMaidNpcService().getMaidLivingEntity().getLocation();
    Location playerLocation = player.getLocation();
    if (maidLocation == null
        || maidLocation.getWorld() == null
        || playerLocation.getWorld() == null
        || !maidLocation.getWorld().equals(playerLocation.getWorld())) {
      clearSelfDefenseTarget(player);
      return false;
    }

    double maxDistance = settings.maxChaseDistance();
    if (maidLocation.distanceSquared(playerLocation) > maxDistance * maxDistance) {
      clearSelfDefenseTarget(player);
      return false;
    }

    return true;
  }

  private void clearSelfDefenseTarget(Player player) {
    selfDefenseTargets.remove(player.getUniqueId());
    BukkitTask task = selfDefenseCleanupTasks.remove(player.getUniqueId());
    if (task != null) {
      task.cancel();
    }
    plugin.getMaidNpcService().forgiveCombatTarget(player);
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
