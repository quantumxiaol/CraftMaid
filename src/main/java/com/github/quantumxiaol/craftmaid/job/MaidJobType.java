package com.github.quantumxiaol.craftmaid.job;

public enum MaidJobType {
  IDLE("idle"),
  FISHING("fishing"),
  FOLLOWING("following"),
  GUARDING("guarding");

  private final String key;

  MaidJobType(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
