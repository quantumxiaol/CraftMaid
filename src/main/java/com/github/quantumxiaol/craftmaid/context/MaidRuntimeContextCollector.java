package com.github.quantumxiaol.craftmaid.context;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.anchor.RegionType;
import java.util.List;
import org.bukkit.entity.Player;

public final class MaidRuntimeContextCollector {
  private final CraftMaid plugin;

  public MaidRuntimeContextCollector(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public String collect(Player player) {
    return """
        【女仆运行时状态】
        当前工作：%s
        %s
        可用工作配置：%s
        %s
        """
        .formatted(
            plugin.getJobService().statusLine(),
            plugin.getMaidInventoryService().summaryLine(),
            availableJobConfigs(),
            plugin.getJobEventBuffer().recentSummary(8))
        .trim();
  }

  private String availableJobConfigs() {
    MaidAnchorService anchors = plugin.getAnchorService();
    return "fishing="
        + formatNames(
            anchors.anchorNames(AnchorType.FISHING_SPOT).stream()
                .filter(name -> anchors.getAnchor(AnchorType.FISHING_SPOT, name).isPresent())
                .filter(name -> anchors.getRegion(RegionType.POND, name).isPresent())
                .toList())
        + "; harvest="
        + formatNames(
            anchors.regionNames(RegionType.FARM).stream()
                .filter(name -> anchors.getRegion(RegionType.FARM, name).isPresent())
                .toList())
        + "; chunk_keeper="
        + formatNames(
            anchors.anchorNames(AnchorType.REDSTONE_WATCH).stream()
                .filter(name -> anchors.getAnchor(AnchorType.REDSTONE_WATCH, name).isPresent())
                .toList());
  }

  private String formatNames(List<String> names) {
    if (names == null || names.isEmpty()) {
      return "无";
    }
    return String.join(",", names);
  }
}
