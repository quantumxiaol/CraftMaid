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
    if (!allReadOnly(actions) && !canControl(player)) {
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
      if (!action.type().isReadOnly()) {
        plugin.getJobEventBuffer().add("动作执行结果 " + line);
      }
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
      case FOLLOW_START -> startFollowing(player);
      case FOLLOW_STOP -> stopFollowing();
      case GUARD_START -> startGuarding(player);
      case GUARD_STOP -> stopGuarding();
      case GUARD_HERE -> startGuardingHere(player);
      case JOB_STOP -> plugin.getJobService().stopActiveJob("好的主人，我先停下手头的事。");
      case JOB_STATUS -> JobActionResult.success(plugin.getJobService().statusLine());
      case INSPECT_SURROUNDINGS ->
          JobActionResult.success(plugin.getPerceptionService().inspectSurroundings(player));
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

  private JobActionResult startFollowing(Player player) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法跟随主人。");
    }
    plugin.getJobService().stopActiveJobForExternalControl("主人让我跟随，我先停下手头的事。");
    boolean started = plugin.getMaidNpcService().startFollowing(player);
    if (!started) {
      return JobActionResult.failure("启动跟随失败，请检查 Citizens 是否正常加载。");
    }
    return JobActionResult.success("已开始跟随主人。");
  }

  private JobActionResult stopFollowing() {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法停止跟随。");
    }
    plugin.getMaidNpcService().stopFollowing();
    return JobActionResult.success("已停止跟随。");
  }

  private JobActionResult startGuarding(Player player) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法护卫主人。");
    }
    if (!plugin.getMaidNpcService().isGuardAvailable()) {
      return JobActionResult.failure("未安装或未启用 Sentinel，无法护卫主人。");
    }
    plugin.getJobService().stopJobsForGuarding("主人让我护卫，我先停下手头的事。");
    boolean started = plugin.getMaidNpcService().startGuarding(player);
    if (!started) {
      return JobActionResult.failure("启动护卫失败，请检查 Sentinel 是否正常加载。");
    }
    return JobActionResult.success("已开始保护主人。");
  }

  private JobActionResult stopGuarding() {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法停止护卫。");
    }
    if (!plugin.getMaidNpcService().isGuardAvailable()) {
      return JobActionResult.failure("未安装或未启用 Sentinel，无法停止护卫。");
    }
    boolean stopped = plugin.getMaidNpcService().stopGuarding();
    if (!stopped) {
      return JobActionResult.failure("停止护卫失败，请检查 Sentinel 是否正常加载。");
    }
    return JobActionResult.success("已停止护卫。");
  }

  private JobActionResult startGuardingHere(Player player) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      return JobActionResult.failure("未安装或未启用 Citizens，无法守在这里。");
    }
    if (!plugin.getMaidNpcService().isGuardAvailable()) {
      return JobActionResult.failure("未安装或未启用 Sentinel，无法守在这里。");
    }
    plugin.getJobService().stopJobsForGuarding("主人让我守在这里，我先停下手头的事。");
    plugin.getMaidNpcService().stopFollowing();
    boolean started = plugin.getMaidNpcService().startGuardingHere(player);
    if (!started) {
      return JobActionResult.failure("启动守卫失败，请检查 Sentinel 是否正常加载。");
    }
    return JobActionResult.success("已开始守在这里。");
  }

  private String validate(List<MaidAction> actions) {
    if (actions.size() > MAX_ACTIONS) {
      return "一次最多只能执行 2 个 action。";
    }
    if (actions.size() == 1) {
      return "";
    }
    if (actions.stream().anyMatch(action -> action.type().isReadOnly())) {
      return "只读观察 action 不能和其他 action 合并。";
    }

    MaidAction first = actions.get(0);
    MaidAction second = actions.get(1);
    if (!first.type().isStop() || !second.type().canFollowStopInPlan()) {
      return "只允许单个 action，或 STOP + START/RECALL 的切换组合。";
    }
    return "";
  }

  private boolean allReadOnly(List<MaidAction> actions) {
    return actions.stream().allMatch(action -> action.type().isReadOnly());
  }

  private boolean canControl(Player player) {
    if (!plugin.getIntentSettings().masterOnly()) {
      return true;
    }
    return player.hasPermission("craftmaid.admin")
        || player.getName().equalsIgnoreCase(plugin.getMasterName());
  }
}
