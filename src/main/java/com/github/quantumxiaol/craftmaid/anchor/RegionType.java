package com.github.quantumxiaol.craftmaid.anchor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum RegionType {
  FARM("farm", "farm"),
  POND("pond", "pond"),
  REDSTONE("redstone", "redstone");

  private final String key;
  private final String displayName;

  RegionType(String key, String displayName) {
    this.key = key;
    this.displayName = displayName;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public static Optional<RegionType> fromInput(String input) {
    if (input == null || input.isBlank()) {
      return Optional.empty();
    }

    String normalized = input.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("field")) {
      normalized = FARM.key;
    } else if (normalized.equals("water")) {
      normalized = POND.key;
    } else if (normalized.equals("machine")) {
      normalized = REDSTONE.key;
    }

    String finalNormalized = normalized;
    return Arrays.stream(values()).filter(type -> type.key.equals(finalNormalized)).findFirst();
  }
}
