package com.github.quantumxiaol.craftmaid.anchor;

import java.util.Locale;
import java.util.Optional;

public enum RegionCorner {
  POS1("pos1"),
  POS2("pos2");

  private final String key;

  RegionCorner(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static Optional<RegionCorner> fromInput(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }

    String normalized = input.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("1")) {
      normalized = POS1.key;
    } else if (normalized.equals("2")) {
      normalized = POS2.key;
    }

    String finalNormalized = normalized;
    for (RegionCorner corner : values()) {
      if (corner.key.equals(finalNormalized)) {
        return Optional.of(corner);
      }
    }
    return Optional.empty();
  }
}
