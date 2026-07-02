package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.FishingSettings;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

final class FishingJob implements MaidJob, Runnable {
  private static final int PERIOD_TICKS = 20;
  private static final int MAX_POND_VOLUME = 8192;
  private static final int ARRIVAL_TIMEOUT_TICKS = 20 * 60;
  private static final int MOVE_RETRY_TICKS = 60;
  private static final double ARRIVAL_DISTANCE = 2.5;

  private final CraftMaid plugin;
  private final MaidJobService jobService;
  private final UUID ownerId;
  private final String name;
  private final Location fishingSpot;
  private final AnchorRegion pond;
  private final Location pondCenter;

  private BukkitTask task;
  private JobPhase phase = JobPhase.STARTING;
  private boolean stopped;
  private boolean animationStarted;
  private int travelTicks;
  private int waitTicks;
  private int swingTicks;
  private int catchCount;
  private int fishCount;
  private int junkCount;
  private int treasureCount;
  private String lastLoot = "none";

  FishingJob(
      CraftMaid plugin,
      MaidJobService jobService,
      UUID ownerId,
      String name,
      Location fishingSpot,
      AnchorRegion pond) {
    this.plugin = plugin;
    this.jobService = jobService;
    this.ownerId = ownerId;
    this.name = name;
    this.fishingSpot = fishingSpot.clone();
    this.pond = pond;
    this.pondCenter = centerOf(pond);
    this.waitTicks = nextWaitTicks();
  }

  @Override
  public MaidJobType type() {
    return MaidJobType.FISHING;
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
    if (pond.volume() > MAX_POND_VOLUME) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("pond/" + name + " 区域太大，当前上限 " + MAX_POND_VOLUME + " 格。");
    }
    World world = plugin.getServer().getWorld(pond.worldName());
    if (world == null) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("pond/" + name + " 所在世界未加载: " + pond.worldName());
    }
    if (countWaterBlocks(world) <= 0) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("pond/" + name + " 里没有找到水方块。");
    }
    if (!plugin.getMaidNpcService().moveTo(fishingSpot)) {
      phase = JobPhase.FAILED;
      return JobActionResult.failure("无法让女仆移动到 fishing_spot/" + name + "。");
    }

    phase = JobPhase.TRAVELLING;
    task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, PERIOD_TICKS);
    plugin.getJobEventBuffer().add("开始钓鱼任务 fishing/" + name + "。");
    plugin.getLogger().info("FishingJob started: " + name + " pond=" + pond.shortText());
    return JobActionResult.success("已开始钓鱼任务: " + name);
  }

  @Override
  public void run() {
    if (stopped) {
      return;
    }

    if (!plugin.getMaidNpcService().isNear(fishingSpot, ARRIVAL_DISTANCE)) {
      travelTicks += PERIOD_TICKS;
      if (travelTicks % MOVE_RETRY_TICKS == 0) {
        plugin.getMaidNpcService().moveTo(fishingSpot);
      }
      if (travelTicks >= ARRIVAL_TIMEOUT_TICKS) {
        fail("钓鱼任务停止：女仆一直没有到达 fishing_spot/" + name + "。");
      }
      return;
    }

    if (phase == JobPhase.TRAVELLING || phase == JobPhase.STARTING) {
      phase = JobPhase.RUNNING;
      startOptionalAnimation();
      plugin.getJobEventBuffer().add("到达 fishing_spot/" + name + "，开始钓鱼。");
      notifyOwner("女仆已到达 fishing_spot/" + name + "，开始钓鱼。");
    }

    plugin.getMaidNpcService().lookAt(pondCenter);
    swingTicks += PERIOD_TICKS;
    if (swingTicks >= 80) {
      plugin.getMaidNpcService().swingMainHand();
      swingTicks = 0;
    }

    waitTicks -= PERIOD_TICKS;
    if (waitTicks > 0) {
      return;
    }

    LootRoll lootRoll = nextLoot();
    InventoryInsertResult insertResult =
        plugin.getMaidInventoryService().addAllOrNothing(List.of(lootRoll.item()));
    if (!insertResult.success()) {
      fail("钓鱼任务停止：女仆背包已满或无法写入背包。");
      return;
    }

    recordCatch(lootRoll);
    plugin
        .getJobEventBuffer()
        .add("钓到 " + lootName(lootRoll.item()) + " x" + lootRoll.item().getAmount() + "，已放入背包。");
    notifyOwner(
        "女仆钓到了 " + lootName(lootRoll.item()) + " x" + lootRoll.item().getAmount() + "，已放入背包。");
    plugin
        .getLogger()
        .info(
            "FishingJob catch: "
                + name
                + " loot="
                + lootName(lootRoll.item())
                + " category="
                + lootRoll.category()
                + " total="
                + catchCount);
    waitTicks = nextWaitTicks();
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

  private void stopInternal() {
    stopped = true;
    if (task != null) {
      task.cancel();
      task = null;
    }
    if (animationStarted) {
      plugin.getMaidNpcService().stopFishingAnimation();
      animationStarted = false;
    }
    plugin.getMaidNpcService().stopMoving();
    plugin
        .getLogger()
        .info(
            "FishingJob stopped: "
                + name
                + " catches="
                + catchCount
                + " fish="
                + fishCount
                + " junk="
                + junkCount
                + " treasure="
                + treasureCount);
    plugin
        .getJobEventBuffer()
        .add(
            "钓鱼任务 fishing/"
                + name
                + " 停止，累计 "
                + catchCount
                + " 件，鱼 "
                + fishCount
                + "，杂物 "
                + junkCount
                + "，宝藏 "
                + treasureCount
                + "。");
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

  void notifyOwner(String message) {
    Player owner = plugin.getServer().getPlayer(ownerId);
    if (owner != null && owner.isOnline()) {
      owner.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }
  }

  @Override
  public String statusLine() {
    return "job: fishing/"
        + name
        + " phase="
        + phase.key()
        + " catches="
        + catchCount
        + " fish="
        + fishCount
        + " junk="
        + junkCount
        + " treasure="
        + treasureCount
        + " next="
        + Math.max(0, waitTicks / 20)
        + "s"
        + " last="
        + lastLoot;
  }

  private void startOptionalAnimation() {
    FishingSettings settings = plugin.getFishingSettings();
    if (!settings.denizenAnimation()) {
      return;
    }
    animationStarted = plugin.getMaidNpcService().startFishingAnimation(pondCenter);
    if (!animationStarted) {
      plugin.getLogger().warning("Denizen fishing animation 已启用，但启动失败；继续使用 CraftMaid 内置钓鱼产出。");
    }
  }

  private Location centerOf(AnchorRegion region) {
    World world = plugin.getServer().getWorld(region.worldName());
    return new Location(
        world,
        (region.minX() + region.maxX() + 1) / 2.0,
        (region.minY() + region.maxY() + 1) / 2.0,
        (region.minZ() + region.maxZ() + 1) / 2.0);
  }

  private int countWaterBlocks(World world) {
    int waterBlocks = 0;
    for (int x = pond.minX(); x <= pond.maxX(); x++) {
      for (int y = pond.minY(); y <= pond.maxY(); y++) {
        for (int z = pond.minZ(); z <= pond.maxZ(); z++) {
          if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
            waterBlocks++;
          }
        }
      }
    }
    return waterBlocks;
  }

  private int nextWaitTicks() {
    FishingSettings settings = plugin.getFishingSettings();
    int min = Math.max(20, settings.minWaitTicks());
    int max = Math.max(min, settings.maxWaitTicks());
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  private LootRoll nextLoot() {
    FishingSettings settings = plugin.getFishingSettings();
    double fishWeight = Math.max(0.0, settings.fishWeight());
    double junkWeight = Math.max(0.0, settings.junkWeight());
    double treasureWeight =
        settings.treasureEnabled() ? Math.max(0.0, settings.treasureWeight()) : 0.0;
    double total = fishWeight + junkWeight + treasureWeight;
    if (total <= 0.0) {
      return fishLoot();
    }

    double roll = ThreadLocalRandom.current().nextDouble(total);
    if (roll < fishWeight) {
      return fishLoot();
    }
    if (roll < fishWeight + junkWeight) {
      return junkLoot();
    }
    return treasureLoot();
  }

  private LootRoll fishLoot() {
    Material[] fish = {Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};
    return new LootRoll(
        new ItemStack(fish[ThreadLocalRandom.current().nextInt(fish.length)], 1), "fish");
  }

  private LootRoll junkLoot() {
    Material[] junk = {
      Material.BOWL,
      Material.LEATHER,
      Material.STICK,
      Material.STRING,
      Material.BONE,
      Material.ROTTEN_FLESH,
      Material.LILY_PAD
    };
    return new LootRoll(
        new ItemStack(junk[ThreadLocalRandom.current().nextInt(junk.length)], 1), "junk");
  }

  private LootRoll treasureLoot() {
    Material[] treasure = {Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE};
    return new LootRoll(
        new ItemStack(treasure[ThreadLocalRandom.current().nextInt(treasure.length)], 1),
        "treasure");
  }

  private void recordCatch(LootRoll lootRoll) {
    catchCount++;
    lastLoot = lootName(lootRoll.item());
    switch (lootRoll.category()) {
      case "fish" -> fishCount++;
      case "junk" -> junkCount++;
      case "treasure" -> treasureCount++;
      default -> {}
    }
  }

  private String lootName(ItemStack item) {
    return item.getType().name().toLowerCase(Locale.ROOT);
  }

  private record LootRoll(ItemStack item, String category) {}
}
