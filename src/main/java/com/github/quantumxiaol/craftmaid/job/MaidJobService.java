package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.anchor.RegionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class MaidJobService {
  private static final Set<MaidJobType> GUARD_STOPS =
      Set.of(MaidJobType.FISHING, MaidJobType.HARVEST);

  private final CraftMaid plugin;
  private MaidJob activeJob;

  public MaidJobService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public JobActionResult startFishing(Player player, String name) {
    Location fishingSpot =
        plugin.getAnchorService().getLocationOrNull(AnchorType.FISHING_SPOT, name);
    if (fishingSpot == null) {
      return JobActionResult.failure("缺少 anchor fishing_spot/" + name + "。");
    }

    AnchorRegion pond = plugin.getAnchorService().getRegion(RegionType.POND, name).orElse(null);
    if (pond == null) {
      return JobActionResult.failure("缺少完整 region pond/" + name + "。");
    }

    return startJob(new FishingJob(plugin, this, player.getUniqueId(), name, fishingSpot, pond));
  }

  public JobActionResult startFishingAuto(Player player) {
    NameSelection selection = resolveFishingName();
    if (!selection.success()) {
      return JobActionResult.failure(selection.message());
    }
    return startFishing(player, selection.name());
  }

  public JobActionResult startChunkKeeper(Player player, String name) {
    Location watchPoint =
        plugin.getAnchorService().getLocationOrNull(AnchorType.REDSTONE_WATCH, name);
    if (watchPoint == null) {
      return JobActionResult.failure("缺少 anchor redstone_watch/" + name + "。");
    }

    return startJob(new ChunkKeeperJob(plugin, this, player.getUniqueId(), name, watchPoint));
  }

  public JobActionResult startChunkKeeperAuto(Player player) {
    NameSelection selection =
        resolveSingleAnchorName(
            AnchorType.REDSTONE_WATCH, "redstone_watch", "/maid chunk start <name>");
    if (!selection.success()) {
      return JobActionResult.failure(selection.message());
    }
    return startChunkKeeper(player, selection.name());
  }

  public JobActionResult startHarvest(Player player, String name) {
    AnchorRegion farm = plugin.getAnchorService().getRegion(RegionType.FARM, name).orElse(null);
    if (farm == null) {
      return JobActionResult.failure("缺少完整 region farm/" + name + "。");
    }

    Location harvestSpot =
        plugin.getAnchorService().getLocationOrNull(AnchorType.HARVEST_SPOT, name);
    return startJob(
        new HarvestFarmJob(plugin, this, player.getUniqueId(), name, farm, harvestSpot));
  }

  public JobActionResult startHarvestAuto(Player player) {
    NameSelection selection =
        resolveSingleRegionName(RegionType.FARM, "farm", "/maid harvest start <name>");
    if (!selection.success()) {
      return JobActionResult.failure(selection.message());
    }
    return startHarvest(player, selection.name());
  }

  private JobActionResult startJob(MaidJob job) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法启动 " + job.type().key() + "。");
    }
    if (activeJob != null && activeJob.isRunning()) {
      return JobActionResult.failure(
          "已经有 job 在运行: " + activeJob.type().key() + "/" + activeJob.name());
    }

    JobConflictPolicy policy = JobConflictPolicy.forType(job.type());
    if (!policy.canStart(plugin)) {
      return JobActionResult.failure(policy.blockedMessage(job.type()));
    }
    policy.applyBeforeStart(plugin);
    if (policy.requiresExclusiveBodyControl()
        && !plugin.getMaidNpcService().prepareForJobControl(true)) {
      return JobActionResult.failure("无法取得女仆 NPC 的身体控制权，请先停止护卫或重生成 NPC。");
    }
    if (!policy.requiresExclusiveBodyControl()) {
      plugin.getMaidNpcService().stopMoving();
    }

    JobActionResult startResult = job.start();
    if (!startResult.success()) {
      return startResult;
    }
    activeJob = job;
    return startResult;
  }

  public JobActionResult stopActiveJob(String reason) {
    if (activeJob != null) {
      MaidJob job = activeJob;
      job.stop(reason);
      return JobActionResult.success("已停止 " + job.type().key() + "。");
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

  public JobActionResult stopJob(MaidJobType type, String reason) {
    if (activeJob == null || activeJob.type() != type) {
      return JobActionResult.failure("当前没有正在运行的 " + type.key() + "。");
    }
    activeJob.stop(reason);
    return JobActionResult.success("已停止 " + type.key() + "。");
  }

  public JobActionResult stopFishing(String reason) {
    return stopJob(MaidJobType.FISHING, reason);
  }

  public JobActionResult stopChunkKeeper(String reason) {
    return stopJob(MaidJobType.CHUNK_KEEPER, reason);
  }

  public JobActionResult stopHarvest(String reason) {
    return stopJob(MaidJobType.HARVEST, reason);
  }

  public void stopActiveJobForExternalControl(String reason) {
    if (activeJob != null) {
      activeJob.stop(reason);
    }
  }

  public void stopJobsForGuarding(String reason) {
    if (activeJob != null && GUARD_STOPS.contains(activeJob.type())) {
      activeJob.stop(reason);
    }
  }

  void onJobStopped(MaidJob job, String reason) {
    if (activeJob == job) {
      activeJob = null;
    }
    notifyOwner(job.ownerId(), reason);
  }

  public void shutdown() {
    if (activeJob != null) {
      activeJob.cancelWithoutNotification();
      activeJob = null;
    }
  }

  public MaidJobType currentType() {
    if (activeJob != null) {
      return activeJob.type();
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
    if (activeJob != null) {
      return activeJob.statusLine();
    }
    MaidJobType type = currentType();
    return "job: " + type.key();
  }

  private void notifyOwner(UUID ownerId, String message) {
    Player owner = plugin.getServer().getPlayer(ownerId);
    if (owner != null && owner.isOnline()) {
      owner.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }
  }

  private NameSelection resolveFishingName() {
    MaidAnchorService anchors = plugin.getAnchorService();
    if (anchors.getAnchor(AnchorType.FISHING_SPOT, MaidAnchorService.DEFAULT_NAME).isPresent()
        && anchors.getRegion(RegionType.POND, MaidAnchorService.DEFAULT_NAME).isPresent()) {
      return NameSelection.success(MaidAnchorService.DEFAULT_NAME);
    }

    List<String> candidates =
        anchors.anchorNames(AnchorType.FISHING_SPOT).stream()
            .filter(name -> anchors.getAnchor(AnchorType.FISHING_SPOT, name).isPresent())
            .filter(name -> anchors.getRegion(RegionType.POND, name).isPresent())
            .toList();
    if (candidates.size() == 1) {
      return NameSelection.success(candidates.getFirst());
    }
    if (candidates.isEmpty()) {
      return NameSelection.failure(
          "缺少同名的 anchor fishing_spot 和完整 region pond。请用 /maid fishing start <name> 指定，或设置 fishing_spot/default 与 pond/default。");
    }
    return NameSelection.failure(
        "发现多个可用钓鱼配置: "
            + String.join(", ", candidates)
            + "。请用 /maid fishing start <name> 指定，或设置 fishing_spot/default 与 pond/default。");
  }

  private NameSelection resolveSingleAnchorName(AnchorType type, String label, String commandHint) {
    MaidAnchorService anchors = plugin.getAnchorService();
    if (anchors.getAnchor(type, MaidAnchorService.DEFAULT_NAME).isPresent()) {
      return NameSelection.success(MaidAnchorService.DEFAULT_NAME);
    }

    List<String> candidates =
        anchors.anchorNames(type).stream()
            .filter(name -> anchors.getAnchor(type, name).isPresent())
            .toList();
    if (candidates.size() == 1) {
      return NameSelection.success(candidates.getFirst());
    }
    if (candidates.isEmpty()) {
      return NameSelection.failure(
          "缺少 anchor " + label + "。请先设置 " + label + "/default，或使用命令设置一个命名 anchor。");
    }
    return NameSelection.failure(
        "发现多个 "
            + label
            + " 配置: "
            + String.join(", ", candidates)
            + "。请用 "
            + commandHint
            + " 指定，或设置 "
            + label
            + "/default。");
  }

  private NameSelection resolveSingleRegionName(RegionType type, String label, String commandHint) {
    MaidAnchorService anchors = plugin.getAnchorService();
    if (anchors.getRegion(type, MaidAnchorService.DEFAULT_NAME).isPresent()) {
      return NameSelection.success(MaidAnchorService.DEFAULT_NAME);
    }

    List<String> candidates =
        anchors.regionNames(type).stream()
            .filter(name -> anchors.getRegion(type, name).isPresent())
            .toList();
    if (candidates.size() == 1) {
      return NameSelection.success(candidates.getFirst());
    }
    if (candidates.isEmpty()) {
      return NameSelection.failure(
          "缺少完整 region " + label + "。请先设置 " + label + "/default，或使用命令设置一个命名 region。");
    }
    return NameSelection.failure(
        "发现多个 "
            + label
            + " 配置: "
            + String.join(", ", candidates)
            + "。请用 "
            + commandHint
            + " 指定，或设置 "
            + label
            + "/default。");
  }

  public record JobActionResult(boolean success, String message) {
    public static JobActionResult success(String message) {
      return new JobActionResult(true, message);
    }

    public static JobActionResult failure(String message) {
      return new JobActionResult(false, message);
    }
  }

  private record NameSelection(boolean success, String name, String message) {
    static NameSelection success(String name) {
      return new NameSelection(true, name, "");
    }

    static NameSelection failure(String message) {
      return new NameSelection(false, "", message);
    }
  }
}
