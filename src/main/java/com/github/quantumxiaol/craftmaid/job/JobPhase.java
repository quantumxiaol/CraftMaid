package com.github.quantumxiaol.craftmaid.job;

public enum JobPhase {
  STARTING("starting"),
  TRAVELLING("travelling"),
  RUNNING("running"),
  STOPPING("stopping"),
  STOPPED("stopped"),
  FAILED("failed");

  private final String key;

  JobPhase(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
