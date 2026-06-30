package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;

enum JobConflictPolicy {
  FISHING(true, true),
  CHUNK_KEEPER(false, true),
  HARVEST(true, true);

  private final boolean blocksGuarding;
  private final boolean stopsFollowing;

  JobConflictPolicy(boolean blocksGuarding, boolean stopsFollowing) {
    this.blocksGuarding = blocksGuarding;
    this.stopsFollowing = stopsFollowing;
  }

  static JobConflictPolicy forType(MaidJobType type) {
    return switch (type) {
      case FISHING -> FISHING;
      case CHUNK_KEEPER -> CHUNK_KEEPER;
      case HARVEST -> HARVEST;
      case IDLE, FOLLOWING, GUARDING ->
          throw new IllegalArgumentException("No job policy: " + type);
    };
  }

  boolean canStart(CraftMaid plugin) {
    return !blocksGuarding || !plugin.getMaidNpcService().isGuarding();
  }

  String blockedMessage(MaidJobType type) {
    if (blocksGuarding) {
      return "女仆正在护卫，先停止护卫再开始 " + type.key() + "。";
    }
    return "当前状态不允许开始 " + type.key() + "。";
  }

  void applyBeforeStart(CraftMaid plugin) {
    if (stopsFollowing) {
      plugin.getMaidNpcService().stopFollowing();
    }
  }
}
