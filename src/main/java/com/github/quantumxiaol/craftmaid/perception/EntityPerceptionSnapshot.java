package com.github.quantumxiaol.craftmaid.perception;

import java.util.List;

public record EntityPerceptionSnapshot(List<EntityGroup> groups) {
  public String summary() {
    if (groups == null || groups.isEmpty()) {
      return "实体：附近没有明显实体。";
    }
    StringBuilder builder = new StringBuilder("实体：");
    for (int i = 0; i < groups.size(); i++) {
      if (i > 0) {
        builder.append("；");
      }
      EntityGroup group = groups.get(i);
      builder.append(group.label()).append(" ").append(group.summary());
    }
    builder.append("。");
    return builder.toString();
  }

  public record EntityGroup(String label, List<EntityTypeCount> topTypes) {
    String summary() {
      if (topTypes == null || topTypes.isEmpty()) {
        return "无";
      }
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < topTypes.size(); i++) {
        if (i > 0) {
          builder.append(", ");
        }
        EntityTypeCount count = topTypes.get(i);
        builder.append(count.name()).append(" x").append(count.count());
      }
      return builder.toString();
    }
  }

  public record EntityTypeCount(String name, int count) {}
}
