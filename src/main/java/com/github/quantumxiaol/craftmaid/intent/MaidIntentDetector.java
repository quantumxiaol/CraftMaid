package com.github.quantumxiaol.craftmaid.intent;

import java.util.Locale;
import java.util.Optional;

public final class MaidIntentDetector {
  public Optional<MaidIntent> detect(String text) {
    String normalized = normalize(text);
    if (normalized.isBlank()) {
      return Optional.empty();
    }

    if (isStopIntent(normalized)) {
      return Optional.of(MaidIntent.JOB_STOP);
    }
    if (isQuestionAboutJob(normalized) || isNegatedJobRequest(normalized)) {
      return Optional.empty();
    }
    if (containsAny(normalized, "去钓鱼", "开始钓鱼", "钓鱼去", "帮我钓鱼", "去鱼塘", "钓会鱼", "钓点鱼")) {
      return Optional.of(MaidIntent.FISHING_START);
    }
    if (containsAny(
        normalized, "看住机器", "看着机器", "守住机器", "守机器", "看机器", "看红石", "看住红石", "守红石", "看住刷铁机", "守住刷铁机")) {
      return Optional.of(MaidIntent.CHUNK_KEEPER_START);
    }
    if (containsAny(normalized, "收一下农田", "收农田", "收割农田", "收成熟作物", "收一下作物", "收作物", "收庄稼", "收麦子")) {
      return Optional.of(MaidIntent.HARVEST_START);
    }
    return Optional.empty();
  }

  private boolean isQuestionAboutJob(String normalized) {
    if (!containsAny(normalized, "吗", "?", "？", "能不能", "会不会", "可不可以", "可以不可以")) {
      return false;
    }
    return containsAny(
        normalized, "钓鱼", "鱼塘", "收农田", "收割", "收作物", "收庄稼", "看机器", "看红石", "守机器", "刷铁机");
  }

  private boolean isStopIntent(String normalized) {
    if (containsAny(normalized, "停止工作", "停下工作", "别忙了", "休息一下", "停手", "先停下")) {
      return true;
    }
    return normalized.equals("回来") || containsAny(normalized, "回来吧", "先回来", "回来一下", "回来休息");
  }

  private boolean isNegatedJobRequest(String normalized) {
    if (!containsAny(normalized, "不要", "别", "不用", "先别", "不必")) {
      return false;
    }
    return containsAny(
        normalized, "钓鱼", "鱼塘", "收农田", "收割", "收作物", "收庄稼", "看机器", "看红石", "守机器", "刷铁机");
  }

  private boolean containsAny(String text, String... candidates) {
    for (String candidate : candidates) {
      if (text.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private String normalize(String text) {
    if (text == null) {
      return "";
    }
    return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }
}
