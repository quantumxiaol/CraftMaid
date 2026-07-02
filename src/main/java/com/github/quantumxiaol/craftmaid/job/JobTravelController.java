package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.JobNavigationSettings;
import org.bukkit.Location;

final class JobTravelController {
  private static final double PROGRESS_EPSILON_SQUARED = 0.25;

  private final CraftMaid plugin;
  private final Location target;

  private int travelTicks;
  private int stalledTicks;
  private double lastDistanceSquared = Double.POSITIVE_INFINITY;

  JobTravelController(CraftMaid plugin, Location target) {
    this.plugin = plugin;
    this.target = target.clone();
  }

  boolean hasArrived() {
    return plugin
        .getMaidNpcService()
        .isNear(target, plugin.getJobNavigationSettings().arrivalDistance());
  }

  boolean tickTravelling(int periodTicks) {
    travelTicks += periodTicks;
    JobNavigationSettings settings = plugin.getJobNavigationSettings();
    int retryTicks = Math.max(periodTicks, settings.retryTicks());
    if (travelTicks % retryTicks == 0) {
      retryIfStalled(retryTicks);
    }
    return travelTicks < settings.arrivalTimeoutSeconds() * 20;
  }

  void reset() {
    travelTicks = 0;
    stalledTicks = 0;
    lastDistanceSquared = Double.POSITIVE_INFINITY;
  }

  private void retryIfStalled(int retryTicks) {
    double distanceSquared = plugin.getMaidNpcService().distanceSquaredTo(target);
    if (Double.isInfinite(lastDistanceSquared)
        || distanceSquared < lastDistanceSquared - PROGRESS_EPSILON_SQUARED) {
      lastDistanceSquared = distanceSquared;
      stalledTicks = 0;
      return;
    }

    stalledTicks += retryTicks;
    if (!plugin.getMaidNpcService().isNavigating() || stalledTicks >= retryTicks * 2) {
      plugin.getMaidNpcService().moveTo(target);
      lastDistanceSquared = distanceSquared;
      stalledTicks = 0;
    }
  }
}
