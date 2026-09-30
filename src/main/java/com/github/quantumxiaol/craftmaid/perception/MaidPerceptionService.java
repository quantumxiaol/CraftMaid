package com.github.quantumxiaol.craftmaid.perception;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class MaidPerceptionService {
  private final CraftMaid plugin;
  private final EntityContextCollector entityCollector = new EntityContextCollector();
  private final BlockContextCollector blockCollector = new BlockContextCollector();

  public MaidPerceptionService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public String collectForPrompt(Player player) {
    CraftMaidConfig.PerceptionSettings settings = plugin.getPerceptionSettings();
    if (settings == null || !settings.enabled()) {
      return collectLegacyEnvironment(player);
    }
    boolean includeDetailedBlocks = shouldAlwaysCollectBlocks(settings.blocks());
    return collect(player, includeDetailedBlocks, false);
  }

  public String inspectSurroundings(Player player) {
    CraftMaidConfig.PerceptionSettings settings = plugin.getPerceptionSettings();
    if (settings == null || !settings.enabled()) {
      return "环境感知未启用。";
    }
    return collect(player, canCollectBlocks(settings.blocks()), true);
  }

  private String collect(Player player, boolean includeDetailedBlocks, boolean fresh) {
    CraftMaidConfig.PerceptionSettings settings = plugin.getPerceptionSettings();
    LivingEntity maid = plugin.getMaidNpcService().getMaidLivingEntity();
    if (maid == null || !maid.isValid() || maid.isDead()) return "女仆尚未生成，无法观察她周围的环境。";
    Location center = maid.getEyeLocation().clone();
    World world = center.getWorld();
    String timeSummary = world.getTime() < 12000 ? "白天" : "夜晚";
    String weatherSummary = world.hasStorm() ? "正在下雨" : "天气晴朗";
    EntityPerceptionSnapshot entities =
        settings.entities().enabled()
            ? entityCollector.collect(center, maid.getUniqueId(), settings.entities())
            : new EntityPerceptionSnapshot(java.util.List.of());
    // Player crosshair data is separate context and must not leak into an observation of the maid.
    String targetBlock =
        !fresh && settings.target().enabled() ? targetBlock(player, settings.target()) : "";
    BlockPerceptionSnapshot blocks =
        includeDetailedBlocks && canCollectBlocks(settings.blocks())
            ? blockCollector.collect(center, settings.blocks(), fresh)
            : null;
    return String.format(
        Locale.ROOT,
        "观察中心：女仆眼睛，世界=%s，坐标=(%.2f, %.2f, %.2f)。\n%s",
        world.getName(),
        center.getX(),
        center.getY(),
        center.getZ(),
        new PerceptionSnapshot(timeSummary, weatherSummary, entities, targetBlock, blocks)
            .summary());
  }

  private boolean shouldAlwaysCollectBlocks(CraftMaidConfig.BlockPerceptionSettings settings) {
    return settings != null
        && settings.enabled()
        && "always".equals(normalizeMode(settings.mode()));
  }

  private boolean canCollectBlocks(CraftMaidConfig.BlockPerceptionSettings settings) {
    return settings != null
        && settings.enabled()
        && !"disabled".equals(normalizeMode(settings.mode()));
  }

  private String normalizeMode(String mode) {
    return mode == null ? "on_demand" : mode.trim().toLowerCase(Locale.ROOT);
  }

  private String targetBlock(Player player, CraftMaidConfig.TargetPerceptionSettings settings) {
    int maxDistance = Math.max(1, Math.min(32, settings.maxDistance()));
    Block target = player.getTargetBlockExact(maxDistance);
    if (target == null || target.getType().isAir()) {
      return "没有明确看向的方块";
    }
    return target.getType().name().toLowerCase(Locale.ROOT)
        + " at "
        + target.getX()
        + ","
        + target.getY()
        + ","
        + target.getZ();
  }

  private String collectLegacyEnvironment(Player player) {
    LivingEntity maid = plugin.getMaidNpcService().getMaidLivingEntity();
    if (maid == null || !maid.isValid() || maid.isDead()) return "女仆尚未生成，无法观察她周围的环境。";
    World world = maid.getWorld();
    return String.format(
        "女仆所在世界现在是%s，%s。",
        world.getTime() < 12000 ? "白天" : "夜晚", world.hasStorm() ? "正在下雨" : "天气晴朗");
  }
}
