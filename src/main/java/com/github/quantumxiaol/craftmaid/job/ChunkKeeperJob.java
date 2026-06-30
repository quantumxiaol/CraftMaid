package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.ChunkKeeperSettings;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

final class ChunkKeeperJob implements MaidJob {
  private final CraftMaid plugin;
  private final MaidJobService jobService;
  private final UUID ownerId;
  private final String name;
  private final Location watchPoint;
  private final List<ChunkKey> tickets = new ArrayList<>();

  private JobPhase phase = JobPhase.STARTING;
  private boolean stopped;
  private boolean guardStarted;

  ChunkKeeperJob(
      CraftMaid plugin, MaidJobService jobService, UUID ownerId, String name, Location watchPoint) {
    this.plugin = plugin;
    this.jobService = jobService;
    this.ownerId = ownerId;
    this.name = name;
    this.watchPoint = watchPoint.clone();
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
  public JobActionResult start() {
    World world = watchPoint.getWorld();
    if (world == null) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("redstone_watch/" + name + " 所在世界未加载。");
    }

    phase = JobPhase.RUNNING;
    int radius = plugin.getChunkKeeperSettings().radiusChunks();
    int centerX = watchPoint.getBlockX() >> 4;
    int centerZ = watchPoint.getBlockZ() >> 4;
    for (int x = centerX - radius; x <= centerX + radius; x++) {
      for (int z = centerZ - radius; z <= centerZ + radius; z++) {
        world.addPluginChunkTicket(x, z, plugin);
        tickets.add(new ChunkKey(world.getName(), x, z));
      }
    }

    if (!plugin.getMaidNpcService().isGuarding()) {
      plugin.getMaidNpcService().moveTo(watchPoint);
      maybeStartGuarding();
    }
    plugin
        .getLogger()
        .info(
            "ChunkKeeperJob started: "
                + name
                + " tickets="
                + tickets.size()
                + " world="
                + world.getName());
    return JobActionResult.success("已开始看守红石机器: " + name + "，加载 chunk 数: " + tickets.size());
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
        + guardStarted;
  }

  private void maybeStartGuarding() {
    ChunkKeeperSettings settings = plugin.getChunkKeeperSettings();
    if (!settings.guardWithSentinel() || plugin.getMaidNpcService().isGuarding()) {
      return;
    }
    guardStarted = plugin.getMaidNpcService().startGuardingAt(watchPoint);
    if (!guardStarted) {
      plugin.getLogger().warning("ChunkKeeperJob 未能启动 Sentinel 守点，chunk 加载仍会继续。");
    }
  }

  private void stopInternal() {
    stopped = true;
    releaseTickets();
    if (guardStarted) {
      plugin.getMaidNpcService().stopGuarding();
      guardStarted = false;
    }
    plugin.getLogger().info("ChunkKeeperJob stopped: " + name);
  }

  private void releaseTickets() {
    for (ChunkKey ticket : tickets) {
      World world = plugin.getServer().getWorld(ticket.worldName());
      if (world != null) {
        world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
      }
    }
    tickets.clear();
  }

  private record ChunkKey(String worldName, int x, int z) {}
}
