package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.RegionType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class MaidJobService {
  private final CraftMaid plugin;
  private FishingJob fishingJob;

  public MaidJobService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public JobActionResult startFishing(Player player, String name) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法启动钓鱼。");
    }
    if (fishingJob != null) {
      return JobActionResult.failure("已经有钓鱼任务在运行。");
    }
    if (plugin.getMaidNpcService().isGuarding()) {
      return JobActionResult.failure("女仆正在护卫，先停止护卫再开始钓鱼。");
    }

    Location fishingSpot =
        plugin.getAnchorService().getLocationOrNull(AnchorType.FISHING_SPOT, name);
    if (fishingSpot == null) {
      return JobActionResult.failure("缺少 anchor fishing_spot/" + name + "。");
    }

    AnchorRegion pond = plugin.getAnchorService().getRegion(RegionType.POND, name).orElse(null);
    if (pond == null) {
      return JobActionResult.failure("缺少完整 region pond/" + name + "。");
    }

    plugin.getMaidNpcService().stopFollowing();
    FishingJob job = new FishingJob(plugin, this, player.getUniqueId(), name, fishingSpot, pond);
    JobActionResult startResult = job.start();
    if (!startResult.success()) {
      return startResult;
    }
    fishingJob = job;
    return startResult;
  }

  public JobActionResult stopActiveJob(String reason) {
    if (fishingJob != null) {
      fishingJob.stop(reason);
      return JobActionResult.success("已停止钓鱼任务。");
    }
    if (plugin.getMaidNpcService().isFollowing()) {
      plugin.getMaidNpcService().stopFollowing();
      return JobActionResult.success("已停止跟随。");
    }
    if (plugin.getMaidNpcService().isGuarding()) {
      if (plugin.getMaidNpcService().stopGuarding()) {
        return JobActionResult.success("已停止护卫。");
      }
      return JobActionResult.failure("停止护卫失败，请检查 Sentinel 是否正常加载。");
    }
    return JobActionResult.failure("当前没有正在运行的 job。");
  }

  public JobActionResult stopFishing(String reason) {
    if (fishingJob == null) {
      return JobActionResult.failure("当前没有正在运行的钓鱼任务。");
    }
    fishingJob.stop(reason);
    return JobActionResult.success("已停止钓鱼任务。");
  }

  public void stopFishingForExternalControl(String reason) {
    if (fishingJob != null) {
      fishingJob.stop(reason);
    }
  }

  void onFishingJobStopped(FishingJob job, String reason) {
    if (fishingJob == job) {
      fishingJob = null;
    }
    job.notifyOwner(reason);
  }

  public void shutdown() {
    if (fishingJob != null) {
      fishingJob.cancelWithoutNotification();
      fishingJob = null;
    }
  }

  public MaidJobType currentType() {
    if (fishingJob != null) {
      return MaidJobType.FISHING;
    }
    if (plugin.getMaidNpcService().isGuarding()) {
      return MaidJobType.GUARDING;
    }
    if (plugin.getMaidNpcService().isFollowing()) {
      return MaidJobType.FOLLOWING;
    }
    return MaidJobType.IDLE;
  }

  public String statusLine() {
    if (fishingJob != null) {
      return fishingJob.statusLine();
    }
    MaidJobType type = currentType();
    return "job: " + type.key();
  }

  public record JobActionResult(boolean success, String message) {
    public static JobActionResult success(String message) {
      return new JobActionResult(true, message);
    }

    public static JobActionResult failure(String message) {
      return new JobActionResult(false, message);
    }
  }
}
