package com.github.quantumxiaol.craftmaid.perception;

public enum SceneGuess {
  REDSTONE_MACHINE("红石机器或机房"),
  FARM("农田"),
  POND("鱼塘或水边"),
  STORAGE("仓库或物资区"),
  WOODEN_BUILDING("木质玩家建筑或住宅"),
  STONE_BUILDING("石质建筑或地下基地"),
  BUILDING("玩家建筑"),
  NATURAL("自然地形"),
  UNKNOWN("不确定");

  private final String label;

  SceneGuess(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
