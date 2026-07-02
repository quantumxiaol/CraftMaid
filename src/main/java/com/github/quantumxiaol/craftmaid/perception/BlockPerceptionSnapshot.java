package com.github.quantumxiaol.craftmaid.perception;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

public record BlockPerceptionSnapshot(
    int radiusXz,
    int up,
    int down,
    int scannedBlocks,
    int nonAirBlocks,
    int skippedChunks,
    List<MaterialCount> topMaterials,
    EnumMap<BlockCategory, Integer> categoryCounts,
    SceneGuess sceneGuess,
    double confidence) {
  public String summary() {
    if (nonAirBlocks <= 0) {
      return "方块统计：扫描范围内没有明显的非空气方块。";
    }

    StringBuilder builder = new StringBuilder();
    builder
        .append("方块统计：以玩家为中心，左右 ")
        .append(radiusXz)
        .append(" 格，上 ")
        .append(up)
        .append(" 格，下 ")
        .append(down)
        .append(" 格；非空气方块 ")
        .append(nonAirBlocks)
        .append("，已扫描 ")
        .append(scannedBlocks)
        .append("。");
    if (skippedChunks > 0) {
      builder.append("跳过未加载 chunk ").append(skippedChunks).append(" 个。");
    }

    builder.append("\n最高频材料：").append(formatTopMaterials());
    builder.append("\n类别统计：").append(formatCategories());
    builder
        .append("\n场景推测：")
        .append(sceneGuess.label())
        .append("，置信度 ")
        .append(String.format(Locale.ROOT, "%.2f", confidence))
        .append("。");
    return builder.toString();
  }

  private String formatTopMaterials() {
    if (topMaterials == null || topMaterials.isEmpty()) {
      return "无";
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < topMaterials.size(); i++) {
      MaterialCount count = topMaterials.get(i);
      if (i > 0) {
        builder.append(", ");
      }
      builder
          .append(count.material().name().toLowerCase(Locale.ROOT))
          .append(" x")
          .append(count.count());
    }
    return builder.toString();
  }

  private String formatCategories() {
    if (categoryCounts == null || categoryCounts.isEmpty()) {
      return "无";
    }
    return "人工建筑 "
        + percent(BlockCategory.BUILDING)
        + "，木质 "
        + percent(BlockCategory.WOOD)
        + "，石质建筑 "
        + percent(BlockCategory.STONE_BUILDING)
        + "，玻璃 "
        + percent(BlockCategory.GLASS)
        + "，容器 "
        + count(BlockCategory.CONTAINER)
        + "，工作站 "
        + count(BlockCategory.WORKSTATION)
        + "，光源 "
        + count(BlockCategory.LIGHTING)
        + "，红石 "
        + count(BlockCategory.REDSTONE)
        + "，作物 "
        + count(BlockCategory.CROP)
        + "，水体 "
        + count(BlockCategory.LIQUID)
        + "。";
  }

  private String percent(BlockCategory category) {
    if (nonAirBlocks <= 0) {
      return "0%";
    }
    double value = count(category) * 100.0 / nonAirBlocks;
    return String.format(Locale.ROOT, "%.0f%%", value);
  }

  private int count(BlockCategory category) {
    return categoryCounts == null ? 0 : categoryCounts.getOrDefault(category, 0);
  }

  public record MaterialCount(Material material, int count) {}
}
