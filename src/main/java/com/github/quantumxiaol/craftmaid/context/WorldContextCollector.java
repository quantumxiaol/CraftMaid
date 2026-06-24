package com.github.quantumxiaol.craftmaid.context;

import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

public final class WorldContextCollector {
  public String collectForPrompt(Player player, int maxContextEntities) {
    World world = player.getWorld();
    boolean isRaining = world.hasStorm();
    String timeStr = (world.getTime() < 12000) ? "白天" : "夜晚";

    List<Entity> nearbyEntities = player.getNearbyEntities(10, 10, 10);
    String environmentEntities =
        nearbyEntities.stream()
            .filter(entity -> entity instanceof Monster || entity instanceof Player)
            .map(this::describeEntity)
            .distinct()
            .limit(maxContextEntities)
            .collect(Collectors.joining(", "));

    String environmentStr = String.format("现在是%s，%s。", timeStr, isRaining ? "正在下雨" : "天气晴朗");
    if (!environmentEntities.isEmpty()) {
      environmentStr += " 你的周围有这些生物或玩家: " + environmentEntities + "。";
    }
    return environmentStr;
  }

  private String describeEntity(Entity entity) {
    if (entity instanceof Player nearbyPlayer) {
      return "玩家 " + nearbyPlayer.getName();
    }
    return entity.getName();
  }
}
