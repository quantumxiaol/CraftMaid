package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
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

final class FishingJob implements Runnable {
  private static final int PERIOD_TICKS = 20;
  private static final int MAX_POND_VOLUME = 8192;
  private static final int ARRIVAL_TIMEOUT_TICKS = 20 * 60;
  private static final double ARRIVAL_DISTANCE = 2.5;

  private final CraftMaid plugin;
  private final MaidJobService jobService;
  private final UUID ownerId;
  private final String name;
  private final Location fishingSpot;
  private final AnchorRegion pond;
  private final Location pondCenter;

  private BukkitTask task;
  private boolean stopped;
  private boolean arrived;
  private int travelTicks;
  private int waitTicks;
  private int swingTicks;
  private int catchCount;

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

  JobActionResult start() {
    if (pond.volume() > MAX_POND_VOLUME) {
      return JobActionResult.failure("pond/" + name + " 区域太大，当前上限 " + MAX_POND_VOLUME + " 格。");
    }
    World world = plugin.getServer().getWorld(pond.worldName());
    if (world == null) {
      return JobActionResult.failure("pond/" + name + " 所在世界未加载: " + pond.worldName());
    }
    if (countWaterBlocks(world) <= 0) {
      return JobActionResult.failure("pond/" + name + " 里没有找到水方块。");
    }
    if (!plugin.getMaidNpcService().moveTo(fishingSpot)) {
      return JobActionResult.failure("无法让女仆移动到 fishing_spot/" + name + "。");
    }

    task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 20L, PERIOD_TICKS);
    return JobActionResult.success("已开始钓鱼任务: " + name);
  }

  @Override
  public void run() {
    if (stopped) {
      return;
    }

    if (!plugin.getMaidNpcService().isNear(fishingSpot, ARRIVAL_DISTANCE)) {
      travelTicks += PERIOD_TICKS;
      plugin.getMaidNpcService().moveTo(fishingSpot);
      if (travelTicks >= ARRIVAL_TIMEOUT_TICKS) {
        stop("钓鱼任务停止：女仆一直没有到达 fishing_spot/" + name + "。");
      }
      return;
    }

    if (!arrived) {
      arrived = true;
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

    ItemStack loot = nextLoot();
    InventoryInsertResult insertResult = plugin.getMaidInventoryService().addItem(loot);
    if (!insertResult.success()) {
      stop("钓鱼任务停止：女仆背包已满或无法写入背包。");
      return;
    }

    catchCount++;
    notifyOwner("女仆钓到了 " + lootName(loot) + " x" + loot.getAmount() + "，已放入背包。");
    waitTicks = nextWaitTicks();
  }

  void stop(String reason) {
    if (stopped) {
      return;
    }
    stopped = true;
    if (task != null) {
      task.cancel();
      task = null;
    }
    jobService.onFishingJobStopped(this, reason);
  }

  void cancelWithoutNotification() {
    stopped = true;
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  void notifyOwner(String message) {
    Player owner = plugin.getServer().getPlayer(ownerId);
    if (owner != null && owner.isOnline()) {
      owner.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }
  }

  String statusLine() {
    String phase = arrived ? "fishing" : "moving";
    return "job: fishing/" + name + " phase=" + phase + " catches=" + catchCount;
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
    return ThreadLocalRandom.current().nextInt(100, 241);
  }

  private ItemStack nextLoot() {
    double roll = ThreadLocalRandom.current().nextDouble();
    if (roll < 0.72) {
      Material[] fish = {
        Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
      };
      return new ItemStack(fish[ThreadLocalRandom.current().nextInt(fish.length)], 1);
    }
    if (roll < 0.95) {
      Material[] junk = {
        Material.BOWL,
        Material.LEATHER,
        Material.STICK,
        Material.STRING,
        Material.BONE,
        Material.ROTTEN_FLESH,
        Material.LILY_PAD
      };
      return new ItemStack(junk[ThreadLocalRandom.current().nextInt(junk.length)], 1);
    }

    Material[] treasure = {Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE};
    return new ItemStack(treasure[ThreadLocalRandom.current().nextInt(treasure.length)], 1);
  }

  private String lootName(ItemStack item) {
    return item.getType().name().toLowerCase(Locale.ROOT);
  }
}
