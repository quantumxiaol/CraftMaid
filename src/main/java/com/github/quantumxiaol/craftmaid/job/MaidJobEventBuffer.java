package com.github.quantumxiaol.craftmaid.job;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class MaidJobEventBuffer {
  private static final int MAX_EVENTS = 24;
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final ArrayDeque<String> events = new ArrayDeque<>();

  public synchronized void add(String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    events.addLast(LocalTime.now().format(TIME_FORMAT) + " " + message.trim());
    while (events.size() > MAX_EVENTS) {
      events.removeFirst();
    }
  }

  public synchronized List<String> recent(int limit) {
    int safeLimit = Math.max(1, limit);
    List<String> snapshot = new ArrayList<>(events);
    int fromIndex = Math.max(0, snapshot.size() - safeLimit);
    return snapshot.subList(fromIndex, snapshot.size());
  }

  public synchronized String recentSummary(int limit) {
    List<String> recent = recent(limit);
    if (recent.isEmpty()) {
      return "最近工作事件：暂无。";
    }

    StringBuilder builder = new StringBuilder("最近工作事件：");
    for (String event : recent) {
      builder.append("\n- ").append(event);
    }
    return builder.toString();
  }
}
