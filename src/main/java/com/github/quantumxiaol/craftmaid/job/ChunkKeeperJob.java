package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.ChunkKeeperSettings;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

final class ChunkKeeperJob implements MaidJob, Runnable {
  private final CraftMaid plugin;
  private final MaidJobService jobService;
  private final UUID ownerId;
  private final String name;
  private final Location watchPoint;
  private final JobChunkTickets tickets;

  private JobPhase phase = JobPhase.STARTING;
  private boolean stopped;
  private boolean guardStarted;
  private long guardRevision;
  private boolean controlsBody;
  private JobTravelController travelController;
  private BukkitTask task;

  ChunkKeeperJob(
      CraftMaid plugin, MaidJobService jobService, UUID ownerId, String name, Location watchPoint) {
    this.plugin = plugin;
    this.jobService = jobService;
    this.ownerId = ownerId;
    this.name = name;
    this.watchPoint = watchPoint.clone();
    this.tickets = new JobChunkTickets(plugin);
  }

  @Override
  public MaidJobType type() {
    return MaidJobType.CHUNK_KEEPER;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public UUID ownerId() {
    return ownerId;
  }

  @Override
  public JobPhase phase() {
    return phase;
  }

  @Override
  public JobActionResult prepare() {
    World world = watchPoint.getWorld();
    if (world == null) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("redstone_watch/" + name + " 所在世界未加载。");
    }

    int radius = plugin.getChunkKeeperSettings().radiusChunks();
    tickets.addAround(watchPoint, radius);
    if (!plugin.getMaidNpcService().isGuarding()) {
      Location standPoint = JobNavigationTargets.findSafeVerticalLocation(watchPoint);
      if (standPoint == null) {
        return JobActionResult.failure("redstone_watch/" + name + " 不是安全站位。");
      }
      travelController = new JobTravelController(plugin, standPoint);
    }
    return JobActionResult.success("看守配置已检查。");
  }

  @Override
  public void discardPreparation() {
    tickets.release();
  }

  @Override
  public JobActionResult start() {
    phase = JobPhase.RUNNING;
    if (!plugin.getMaidNpcService().isGuarding()) {
      if (!plugin.getMaidNpcService().moveTo(travelController.target())) {
        phase = JobPhase.FAILED;
        return JobActionResult.failure("无法让女仆移动到 redstone_watch/" + name + "。");
      }
      controlsBody = true;
      phase = JobPhase.TRAVELLING;
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }
    plugin
        .getJobEventBuffer()
        .add("开始看守红石机器 chunk_keeper/" + name + "，加载 chunk 数 " + tickets.size() + "。");
    plugin
        .getLogger()
        .info(
            "ChunkKeeperJob started: "
                + name
                + " tickets="
                + tickets.size()
                + " world="
                + watchPoint.getWorld().getName());
    return JobActionResult.success(
        "已开始加载红石机器区块: "
            + name
            + "，加载 chunk 数: "
            + tickets.size()
            + (controlsBody ? "，女仆正在前往站位。" : "，女仆继续当前护卫，不在此处驻守。"));
  }

  @Override
  public void run() {
    if (stopped || !controlsBody) {
      return;
    }
    if (guardStarted && guardRevision != plugin.getMaidNpcService().guardingRevision()) {
      releaseBodyControl();
      return;
    }
    if (!travelController.hasArrived()) {
      // Sentinel owns navigation while guarding this point (including nearby combat).
      if (!guardStarted) {
        phase = JobPhase.TRAVELLING;
        if (!travelController.tickTravelling(20)) {
          stop("看守任务停止：女仆未能到达 redstone_watch/" + name + "。");
        }
      }
      return;
    }
    if (phase == JobPhase.TRAVELLING) {
      plugin.getMaidNpcService().stopMoving();
      phase = JobPhase.RUNNING;
      travelController.reset();
      maybeStartGuarding();
    }
  }

  @Override
  public void stop(String reason) {
    if (stopped) {
      return;
    }
    phase = JobPhase.STOPPING;
    stopInternal();
    phase = JobPhase.STOPPED;
    jobService.onJobStopped(this, reason);
  }

  @Override
  public void cancelWithoutNotification() {
    phase = JobPhase.STOPPED;
    stopInternal();
  }

  @Override
  public boolean isRunning() {
    return !stopped;
  }

  @Override
  public String statusLine() {
    return "job: chunk_keeper/"
        + name
        + " phase="
        + phase.key()
        + " chunks="
        + tickets.size()
        + " guard="
        + guardStarted
        + " body="
        + (controlsBody ? "watching" : "external")
        + " present="
        + plugin
            .getMaidNpcService()
            .isNear(
                travelController == null ? watchPoint : travelController.target(),
                plugin.getJobNavigationSettings().arrivalDistance());
  }

  private void maybeStartGuarding() {
    ChunkKeeperSettings settings = plugin.getChunkKeeperSettings();
    if (!settings.guardWithSentinel() || plugin.getMaidNpcService().isGuarding()) {
      return;
    }
    guardStarted = plugin.getMaidNpcService().startGuardingAt(travelController.target());
    guardRevision = plugin.getMaidNpcService().guardingRevision();
    if (!guardStarted) {
      plugin.getLogger().warning("ChunkKeeperJob 未能启动 Sentinel 守点，chunk 加载仍会继续。");
    }
  }

  private void stopInternal() {
    stopped = true;
    tickets.release();
    releaseBodyControl();
    plugin.getJobEventBuffer().add("看守红石机器 chunk_keeper/" + name + " 停止，已释放 chunk ticket。");
    plugin.getLogger().info("ChunkKeeperJob stopped: " + name);
  }

  /** Keep chunk tickets, but relinquish navigation before another guard takes over. */
  void releaseBodyControl() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    if (guardStarted && guardRevision == plugin.getMaidNpcService().guardingRevision()) {
      plugin.getMaidNpcService().stopGuarding();
    } else if (controlsBody && !guardStarted) {
      plugin.getMaidNpcService().stopMoving();
    }
    guardStarted = false;
    controlsBody = false;
    if (!stopped) {
      phase = JobPhase.RUNNING;
    }
  }
}
