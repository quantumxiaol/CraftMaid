package com.github.quantumxiaol.craftmaid.intent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum MaidActionType {
  FISHING_START,
  FISHING_STOP,
  HARVEST_START,
  HARVEST_STOP,
  CHUNK_KEEPER_START,
  CHUNK_KEEPER_STOP,
  RECALL,
  FOLLOW_START,
  FOLLOW_STOP,
  GUARD_START,
  GUARD_STOP,
  GUARD_HERE,
  JOB_STOP,
  JOB_STATUS;

  public boolean isStart() {
    return this == FISHING_START
        || this == HARVEST_START
        || this == CHUNK_KEEPER_START
        || this == FOLLOW_START
        || this == GUARD_START
        || this == GUARD_HERE;
  }

  public boolean isStop() {
    return this == JOB_STOP
        || this == FISHING_STOP
        || this == HARVEST_STOP
        || this == CHUNK_KEEPER_STOP
        || this == FOLLOW_STOP
        || this == GUARD_STOP;
  }

  public boolean canFollowStopInPlan() {
    return isStart() || this == RECALL;
  }

  public static Optional<MaidActionType> fromInput(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }
    String normalized = input.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(type -> type.name().equals(normalized)).findFirst();
  }
}
