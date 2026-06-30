package com.github.quantumxiaol.craftmaid.job;

public enum MaidJobType {
  IDLE("idle"),
  FISHING("fishing"),
  CHUNK_KEEPER("chunk_keeper"),
  HARVEST("harvest"),
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
