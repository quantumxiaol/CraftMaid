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
  JOB_STOP,
  JOB_STATUS;

  public boolean isStart() {
    return this == FISHING_START || this == HARVEST_START || this == CHUNK_KEEPER_START;
  }

  public boolean isStop() {
    return this == JOB_STOP
        || this == FISHING_STOP
        || this == HARVEST_STOP
        || this == CHUNK_KEEPER_STOP;
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
