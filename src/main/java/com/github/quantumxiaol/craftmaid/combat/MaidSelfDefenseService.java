package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class MaidSelfDefenseService {
  private static final long AUDIT_INTERVAL_TICKS = 20L;

  private final CraftMaid plugin;
  private final Map<UUID, SelfDefenseTarget> targets = new HashMap<>();
  private BukkitTask auditTask;

  public MaidSelfDefenseService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public void start() {
    if (auditTask != null) {
      return;
    }
    auditTask =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(plugin, this::auditTargets, AUDIT_INTERVAL_TICKS, AUDIT_INTERVAL_TICKS);
  }

  public boolean startDefense(Player attacker) {
    if (attacker == null || !attacker.isOnline()) {
      return false;
    }
    CraftMaidConfig.SelfDefenseSettings settings = settings();
    if (settings == null || !settings.enabled() || !settings.targetPlayers()) {
      return false;
    }
    if (!plugin.getMaidNpcService().addSelfDefenseTarget(attacker)) {
      return false;
    }

    long expiresAt = System.currentTimeMillis() + settings.durationSeconds() * 1000L;
    targets.put(
        attacker.getUniqueId(),
        new SelfDefenseTarget(attacker.getUniqueId(), attacker.getName(), expiresAt));
    return true;
  }

  public boolean isActiveTarget(Player player) {
    if (player == null) {
      return false;
    }
    SelfDefenseTarget target = targets.get(player.getUniqueId());
    if (target == null) {
      return false;
    }
    if (shouldForgive(target, player, System.currentTimeMillis())) {
      forgive(target);
      return false;
    }
    return true;
  }

  public void forgive(Player player) {
    if (player == null) {
      return;
    }
    SelfDefenseTarget target = targets.remove(player.getUniqueId());
    if (target == null) {
      target = new SelfDefenseTarget(player.getUniqueId(), player.getName(), 0L);
    }
    removeNpcTarget(target);
  }

  public int forgiveAll() {
    List<SelfDefenseTarget> activeTargets = List.copyOf(targets.values());
    targets.clear();
    activeTargets.forEach(this::removeNpcTarget);
    return activeTargets.size();
  }

  public void shutdown() {
    if (auditTask != null) {
      auditTask.cancel();
      auditTask = null;
    }
    forgiveAll();
  }

  private void auditTargets() {
    long now = System.currentTimeMillis();
    for (SelfDefenseTarget target : List.copyOf(targets.values())) {
      Player player = plugin.getServer().getPlayer(target.playerId());
      if (shouldForgive(target, player, now)) {
        forgive(target);
      }
    }
  }

  private boolean shouldForgive(SelfDefenseTarget target, Player player, long now) {
    CraftMaidConfig.SelfDefenseSettings settings = settings();
    if (settings == null || !settings.enabled() || now >= target.expiresAtMillis()) {
      return true;
    }
    if (player == null || !player.isOnline()) {
      return true;
    }
    if (!settings.forgiveWhenAttackerFar()) {
      return false;
    }

    LivingEntity maid = plugin.getMaidNpcService().getMaidLivingEntity();
    if (maid == null) {
      return true;
    }
    Location maidLocation = maid.getLocation();
    Location playerLocation = player.getLocation();
    if (maidLocation.getWorld() == null
        || playerLocation.getWorld() == null
        || !maidLocation.getWorld().equals(playerLocation.getWorld())) {
      return true;
    }

    double maxDistance = settings.maxChaseDistance();
    return maidLocation.distanceSquared(playerLocation) > maxDistance * maxDistance;
  }

  private void forgive(SelfDefenseTarget target) {
    if (!targets.remove(target.playerId(), target)) {
      return;
    }
    removeNpcTarget(target);
  }

  private void removeNpcTarget(SelfDefenseTarget target) {
    plugin.getMaidNpcService().removeSelfDefenseTarget(target.playerId(), target.playerName());
  }

  private CraftMaidConfig.SelfDefenseSettings settings() {
    CraftMaidConfig.CombatSettings combatSettings = plugin.getMaidCombatSettings();
    return combatSettings == null ? null : combatSettings.selfDefense();
  }

  private record SelfDefenseTarget(UUID playerId, String playerName, long expiresAtMillis) {}
}
