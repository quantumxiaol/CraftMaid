package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class MaidCombatBuffService {
  private final CraftMaid plugin;
  private BukkitTask task;

  public MaidCombatBuffService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public void start() {
    CraftMaidConfig.SurvivabilitySettings settings = plugin.getMaidSurvivabilitySettings();
    if (settings == null || !settings.enabled() || !settings.potionBuffs()) {
      return;
    }
    if (task != null) {
      return;
    }

    long refreshTicks = Math.max(20L, settings.refreshTicks());
    task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 1L, refreshTicks);
  }

  public void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  public void restartIfGuarding() {
    stop();
    if (plugin.getMaidNpcService() != null && plugin.getMaidNpcService().isGuarding()) {
      start();
    }
  }

  private void refresh() {
    if (!plugin.isEnabled()
        || plugin.getMaidNpcService() == null
        || !plugin.getMaidNpcService().isGuarding()) {
      stop();
      return;
    }

    CraftMaidConfig.SurvivabilitySettings settings = plugin.getMaidSurvivabilitySettings();
    if (settings == null || !settings.enabled() || !settings.potionBuffs()) {
      stop();
      return;
    }

    LivingEntity maid = plugin.getMaidNpcService().getMaidLivingEntity();
    if (maid == null || maid.isDead()) {
      return;
    }

    int durationTicks = Math.max(60, settings.refreshTicks() + 40);
    maid.addPotionEffect(
        new PotionEffect(
            PotionEffectType.REGENERATION,
            durationTicks,
            settings.regenerationAmplifier(),
            true,
            false,
            false));
    maid.addPotionEffect(
        new PotionEffect(
            PotionEffectType.RESISTANCE,
            durationTicks,
            settings.resistanceAmplifier(),
            true,
            false,
            false));
    if (settings.absorptionHearts() > 0.0
        && maid.getAbsorptionAmount() < settings.absorptionHearts()) {
      maid.setAbsorptionAmount(settings.absorptionHearts());
    }
  }
}
