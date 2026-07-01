package com.github.quantumxiaol.craftmaid.intent;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

public final class MaidActionExecutor {
  private static final int MAX_ACTIONS = 2;

  private final CraftMaid plugin;

  public MaidActionExecutor(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public MaidActionExecutionResult execute(Player player, MaidActionPlan plan) {
    List<MaidAction> actions = plan == null || plan.actions() == null ? List.of() : plan.actions();
    if (actions.isEmpty()) {
      return new MaidActionExecutionResult(false, List.of());
    }
    if (!canControl(player)) {
      return new MaidActionExecutionResult(true, List.of("ACTION_DENIED: 只有主人或管理员可以让女仆执行工作。"));
    }

    String validationError = validate(actions);
    if (!validationError.isBlank()) {
      return new MaidActionExecutionResult(true, List.of("ACTION_REJECTED: " + validationError));
    }

    List<String> results = new ArrayList<>();
    for (MaidAction action : actions) {
      JobActionResult result = executeOne(player, action);
      String line =
          action.type().name()
              + ": "
              + (result.success() ? "success" : "failure")
              + " - "
              + result.message();
      results.add(line);
      plugin.getJobEventBuffer().add("动作执行结果 " + line);
    }
    return new MaidActionExecutionResult(true, List.copyOf(results));
  }

  private JobActionResult executeOne(Player player, MaidAction action) {
    String name = action.nameOrBlank();
    return switch (action.type()) {
      case FISHING_START ->
          name.isBlank()
              ? plugin.getJobService().startFishingAuto(player)
              : plugin.getJobService().startFishing(player, name);
      case HARVEST_START ->
          name.isBlank()
              ? plugin.getJobService().startHarvestAuto(player)
              : plugin.getJobService().startHarvest(player, name);
      case CHUNK_KEEPER_START ->
          name.isBlank()
              ? plugin.getJobService().startChunkKeeperAuto(player)
              : plugin.getJobService().startChunkKeeper(player, name);
      case FISHING_STOP -> plugin.getJobService().stopFishing("好的主人，我先把鱼竿收起来。");
      case HARVEST_STOP -> plugin.getJobService().stopHarvest("好的主人，我先停下收田。");
      case CHUNK_KEEPER_STOP -> plugin.getJobService().stopChunkKeeper("好的主人，我先不看机器了。");
      case RECALL -> recallMaid(player);
      case JOB_STOP -> plugin.getJobService().stopActiveJob("好的主人，我先停下手头的事。");
      case JOB_STATUS -> JobActionResult.success(plugin.getJobService().statusLine());
    };
  }

  private JobActionResult recallMaid(Player player) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法召回女仆。");
    }
    plugin.getJobService().stopActiveJobForExternalControl("主人叫我过去，我先停下手头的事。");
    boolean moved = plugin.getMaidNpcService().spawnAt(player, plugin.getMaidName());
    if (!moved) {
      return JobActionResult.failure("召回失败，请检查 Citizens 是否正常加载。");
    }
    return JobActionResult.success("已回到主人身边。");
  }

  private String validate(List<MaidAction> actions) {
    if (actions.size() > MAX_ACTIONS) {
      return "一次最多只能执行 2 个 action。";
    }
    if (actions.size() == 1) {
      return "";
    }

    MaidAction first = actions.get(0);
    MaidAction second = actions.get(1);
    if (!first.type().isStop() || !second.type().canFollowStopInPlan()) {
      return "只允许单个 action，或 STOP + START/RECALL 的切换组合。";
    }
    return "";
  }

  private boolean canControl(Player player) {
    if (!plugin.getIntentSettings().masterOnly()) {
      return true;
    }
    return player.hasPermission("craftmaid.admin")
        || player.getName().equalsIgnoreCase(plugin.getMasterName());
  }
}
