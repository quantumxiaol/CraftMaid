package com.github.quantumxiaol.craftmaid.anchor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AnchorType {
  HOME("home", "home"),
  FISHING_SPOT("fishing_spot", "fishing_spot"),
  CHEST("chest", "chest"),
  GUARD_POST("guard_post", "guard_post"),
  REDSTONE_WATCH("redstone_watch", "redstone_watch");

  private final String key;
  private final String displayName;

  AnchorType(String key, String displayName) {
    this.key = key;
    this.displayName = displayName;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public static Optional<AnchorType> fromInput(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }

    String normalized = input.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("fishing") || normalized.equals("pond")) {
      normalized = FISHING_SPOT.key;
    } else if (normalized.equals("redstone") || normalized.equals("redstone_post")) {
      normalized = REDSTONE_WATCH.key;
    } else if (normalized.equals("guard")) {
      normalized = GUARD_POST.key;
    }

    String finalNormalized = normalized;
    return Arrays.stream(values()).filter(type -> type.key.equals(finalNormalized)).findFirst();
  }
}
