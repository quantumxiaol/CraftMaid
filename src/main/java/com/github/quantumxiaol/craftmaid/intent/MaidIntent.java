package com.github.quantumxiaol.craftmaid.intent;

public enum MaidIntent {
  FISHING_START("fishing_start"),
  CHUNK_KEEPER_START("chunk_keeper_start"),
  HARVEST_START("harvest_start"),
  JOB_STOP("job_stop");

  private final String key;

  MaidIntent(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
