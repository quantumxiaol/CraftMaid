package com.github.quantumxiaol.craftmaid.vision;

import com.github.quantumxiaol.craftmaid.conversation.ConversationImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Frozen text and images of one capture, detached from both Bukkit and retained capture files. */
public record VisionObservation(String summary, List<ConversationImage> images) {
  public VisionObservation {
    images = List.copyOf(images);
  }

  static VisionObservation from(
      ObservationSnapshot snapshot,
      VisionSettings settings,
      SoftwareRenderer.Panorama panorama,
      Set<String> fallbacks,
      Path directory,
      RenderBudget budget)
      throws IOException {
    List<ConversationImage> images = new ArrayList<>();
    StringBuilder summary = new StringBuilder(describeSnapshot(snapshot, settings));
    summary.append("\n同一快照渲染得到的可见表面材料提示（按像素命中排序，不是方块数量）：");
    for (CompassView view : CompassView.values()) {
      budget.check();
      byte[] png;
      try (var input = Files.newInputStream(directory.resolve(view.fileName()))) {
        png = input.readNBytes(ConversationImage.MAX_PNG_BYTES + 1);
      }
      images.add(ConversationImage.png(directionLabel(view), png));
      var frame = panorama.frames().get(view);
      summary.append('\n').append(view.name()).append(": ");
      summary.append(
          frame.surfaceHits().entrySet().stream()
              .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
              .limit(6)
              .map(Map.Entry::getKey)
              .collect(Collectors.joining(", ")));
      summary.append("；未知区域像素=").append(frame.unknownPixels());
    }
    if (!fallbacks.isEmpty()) {
      summary
          .append("\n以下材料使用简化外形，不能仅凭图判断其细节：")
          .append(fallbacks.stream().sorted().limit(16).collect(Collectors.joining(", ")));
    }
    budget.check();
    return new VisionObservation(summary.toString(), images);
  }

  static String describeSnapshot(ObservationSnapshot snapshot, VisionSettings settings) {
    var origin = snapshot.origin();
    String entities =
        snapshot.entities().stream()
            .collect(
                Collectors.groupingBy(
                    ObservationSnapshot.EntityInfo::type,
                    java.util.TreeMap::new,
                    Collectors.counting()))
            .entrySet()
            .stream()
            .limit(16)
            .map(entry -> entry.getKey() + "×" + entry.getValue())
            .collect(Collectors.joining(", "));
    return """
        【女仆周围的同一次观察】
        观察中心是女仆眼睛，不是玩家位置；世界=%s，眼睛坐标=(%.2f, %.2f, %.2f)。
        采集时间=%s，世界 tick=%d；这是采集时的场景，等待期间女仆可能已移动。
        维度=%s，游戏时间=%d，天气=%s，拍摄距离=%d 格。
        固定世界方向：北=-Z、东=+X、南=+Z、西=-X；不代表女仆的前后左右。
        缺失区块=%d；灰色区域表示未知，不能当成墙或虚空。
        同一位置附近实体传感器记录（最多64个，不代表视线可见）：%s。
        图片只绘制方块，未绘制实体/皮肤/告示牌文字；光照、流体和特殊方块外形有简化。
        不要把附近实体说成图片里看见的对象；不能从图上确定机器功能、所有权或被遮挡的内部。
        """
        .formatted(
            origin.worldName(),
            origin.x(),
            origin.y(),
            origin.z(),
            origin.capturedAt(),
            origin.worldTick(),
            snapshot.environment(),
            snapshot.worldTime(),
            snapshot.raining() ? "雨/雪" : "晴",
            settings.distance(),
            snapshot.missingChunks(),
            entities.isEmpty() ? "未记录到实体" : entities)
        .trim();
  }

  private static String directionLabel(CompassView view) {
    return switch (view) {
      case NORTH -> "第1张：正北 NORTH（-Z）";
      case EAST -> "第2张：正东 EAST（+X）";
      case SOUTH -> "第3张：正南 SOUTH（+Z）";
      case WEST -> "第4张：正西 WEST（-X）";
    };
  }
}
