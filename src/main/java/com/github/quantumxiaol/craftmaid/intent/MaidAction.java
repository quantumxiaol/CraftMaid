package com.github.quantumxiaol.craftmaid.intent;

public record MaidAction(MaidActionType type, String name, String target) {
  public String nameOrBlank() {
    return name == null ? "" : name.trim();
  }
}
