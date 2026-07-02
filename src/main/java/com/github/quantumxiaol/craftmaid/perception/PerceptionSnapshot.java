package com.github.quantumxiaol.craftmaid.perception;

public record PerceptionSnapshot(
    String timeSummary,
    String weatherSummary,
    EntityPerceptionSnapshot entities,
    String targetBlock,
    BlockPerceptionSnapshot blocks) {
  public String summary() {
    StringBuilder builder = new StringBuilder();
    builder.append("时间：").append(timeSummary).append("，").append(weatherSummary).append("。");
    if (entities != null) {
      builder.append("\n").append(entities.summary());
    }
    if (targetBlock != null && !targetBlock.isBlank()) {
      builder.append("\n玩家视线：").append(targetBlock).append("。");
    }
    if (blocks != null) {
      builder.append("\n").append(blocks.summary());
    }
    return builder.toString();
  }
}
