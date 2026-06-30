package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.HarvestSettings;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

final class HarvestFarmJob implements MaidJob, Runnable {
  private static final int PERIOD_TICKS = 1;
  private static final int ARRIVAL_TIMEOUT_TICKS = 20 * 60;
  private static final double ARRIVAL_DISTANCE = 3.0;
  private static final Set<Material> CROPS =
      EnumSet.of(
          Material.WHEAT,
          Material.CARROTS,
          Material.POTATOES,
          Material.BEETROOTS,
          Material.NETHER_WART);

  private final CraftMaid plugin;
  private final MaidJobService jobService;
  private final UUID ownerId;
  private final String name;
  private final AnchorRegion farm;
  private final Location farmCenter;

  private BukkitTask task;
  private JobPhase phase = JobPhase.STARTING;
  private boolean stopped;
  private boolean finishedScanning;
  private int cursorX;
  private int cursorY;
  private int cursorZ;
  private int travelTicks;
  private int scannedBlocks;
  private int harvestedBlocks;
  private int itemCount;
  private String lastCrop = "none";

  HarvestFarmJob(
      CraftMaid plugin, MaidJobService jobService, UUID ownerId, String name, AnchorRegion farm) {
    this.plugin = plugin;
    this.jobService = jobService;
    this.ownerId = ownerId;
    this.name = name;
    this.farm = farm;
    this.farmCenter = centerOf(farm);
    this.cursorX = farm.minX();
    this.cursorY = farm.minY();
    this.cursorZ = farm.minZ();
  }

  @Override
  public MaidJobType type() {
    return MaidJobType.HARVEST;
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
    HarvestSettings settings = plugin.getHarvestSettings();
    if (farm.volume() > settings.maxRegionVolume()) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure(
          "farm/" + name + " 区域太大，当前上限 " + settings.maxRegionVolume() + " 格。");
    }
    World world = plugin.getServer().getWorld(farm.worldName());
    if (world == null) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("farm/" + name + " 所在世界未加载: " + farm.worldName());
    }
    if (!plugin.getMaidNpcService().moveTo(farmCenter)) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("无法让女仆移动到 farm/" + name + "。");
    }

    phase = JobPhase.TRAVELLING;
    task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, PERIOD_TICKS);
    plugin.getJobEventBuffer().add("开始收割农田 harvest/" + name + "。");
    plugin.getLogger().info("HarvestFarmJob started: " + name + " farm=" + farm.shortText());
    return JobActionResult.success("已开始收割农田: " + name);
  }

  @Override
  public void run() {
    if (stopped) {
      return;
    }

    if (!plugin.getMaidNpcService().isNear(farmCenter, ARRIVAL_DISTANCE)) {
      travelTicks += PERIOD_TICKS;
      plugin.getMaidNpcService().moveTo(farmCenter);
      if (travelTicks >= ARRIVAL_TIMEOUT_TICKS) {
        fail("收割任务停止：女仆一直没有到达 farm/" + name + "。");
      }
      return;
    }

    if (phase == JobPhase.TRAVELLING || phase == JobPhase.STARTING) {
      phase = JobPhase.RUNNING;
      plugin.getJobEventBuffer().add("到达 farm/" + name + "，开始扫描成熟作物。");
    }

    harvestTick();
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

  private void fail(String reason) {
    if (stopped) {
      return;
    }
    phase = JobPhase.FAILED;
    stopInternal();
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
    return "job: harvest/"
        + name
        + " phase="
        + phase.key()
        + " scanned="
        + scannedBlocks
        + " harvested="
        + harvestedBlocks
        + " items="
        + itemCount
        + " last="
        + lastCrop;
  }

  private void harvestTick() {
    World world = plugin.getServer().getWorld(farm.worldName());
    if (world == null) {
      fail("收割任务停止：farm/" + name + " 所在世界未加载。");
      return;
    }

    HarvestSettings settings = plugin.getHarvestSettings();
    int processed = 0;
    int scannedThisTick = 0;
    int maxScanBlocks = Math.max(128, settings.maxBlocksPerTick() * 64);
    while (!finishedScanning
        && processed < settings.maxBlocksPerTick()
        && harvestedBlocks < settings.maxBlocksPerRun()
        && scannedThisTick < maxScanBlocks) {
      Block block = nextBlock(world);
      scannedThisTick++;
      scannedBlocks++;
      if (!isMatureWhitelistedCrop(block)) {
        continue;
      }

      List<ItemStack> drops = normalizedDrops(block.getDrops());
      if (drops.isEmpty()) {
        continue;
      }
      if (!plugin.getMaidInventoryService().canFitAll(drops)) {
        fail("收割任务停止：女仆背包空间不足，当前作物未改动。");
        return;
      }

      InventoryInsertResult insertResult = plugin.getMaidInventoryService().addAllOrNothing(drops);
      if (!insertResult.success()) {
        fail("收割任务停止：女仆背包空间不足，当前作物未改动。");
        return;
      }

      resetCrop(block);
      processed++;
      harvestedBlocks++;
      itemCount += drops.stream().mapToInt(ItemStack::getAmount).sum();
      lastCrop = block.getType().name().toLowerCase(Locale.ROOT);
    }

    if (harvestedBlocks >= settings.maxBlocksPerRun()) {
      stop("收割任务达到本次上限，已收割 " + harvestedBlocks + " 个作物。");
      return;
    }
    if (finishedScanning) {
      stop("收割任务完成，已收割 " + harvestedBlocks + " 个作物。");
    }
  }

  private boolean isMatureWhitelistedCrop(Block block) {
    if (!CROPS.contains(block.getType())) {
      return false;
    }
    BlockData blockData = block.getBlockData();
    return blockData instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
  }

  private void resetCrop(Block block) {
    BlockData blockData = block.getBlockData().clone();
    if (blockData instanceof Ageable ageable) {
      ageable.setAge(0);
      block.setBlockData((BlockData) ageable, false);
    }
  }

  private Block nextBlock(World world) {
    Block block = world.getBlockAt(cursorX, cursorY, cursorZ);
    advanceCursor();
    return block;
  }

  private void advanceCursor() {
    cursorX++;
    if (cursorX <= farm.maxX()) {
      return;
    }
    cursorX = farm.minX();
    cursorZ++;
    if (cursorZ <= farm.maxZ()) {
      return;
    }
    cursorZ = farm.minZ();
    cursorY++;
    if (cursorY > farm.maxY()) {
      finishedScanning = true;
    }
  }

  private List<ItemStack> normalizedDrops(Collection<ItemStack> drops) {
    List<ItemStack> normalized = new ArrayList<>();
    for (ItemStack drop : drops) {
      if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
        continue;
      }
      normalized.add(drop.clone());
    }
    return normalized;
  }

  private void stopInternal() {
    stopped = true;
    if (task != null) {
      task.cancel();
      task = null;
    }
    plugin
        .getLogger()
        .info(
            "HarvestFarmJob stopped: "
                + name
                + " harvested="
                + harvestedBlocks
                + " items="
                + itemCount);
    plugin
        .getJobEventBuffer()
        .add(
            "收割任务 harvest/"
                + name
                + " 停止，收割 "
                + harvestedBlocks
                + " 个作物，放入 "
                + itemCount
                + " 件物品。");
  }

  private Location centerOf(AnchorRegion region) {
    World world = plugin.getServer().getWorld(region.worldName());
    return new Location(
        world,
        (region.minX() + region.maxX() + 1) / 2.0,
        (region.minY() + region.maxY() + 1) / 2.0,
        (region.minZ() + region.maxZ() + 1) / 2.0);
  }
}
