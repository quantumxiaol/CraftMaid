package com.github.quantumxiaol.craftmaid.vision;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class RenderBudget {
  private final long deadline;
  private final BooleanSupplier cancelled;
  private final String timeoutMessage;

  RenderBudget(int seconds, BooleanSupplier cancelled) {
    this(seconds, cancelled, "图片渲染超时，请降低分辨率或拍摄距离。");
  }

  RenderBudget(int seconds, BooleanSupplier cancelled, String timeoutMessage) {
    this.deadline = System.nanoTime() + seconds * 1_000_000_000L;
    this.cancelled = cancelled;
    this.timeoutMessage = timeoutMessage;
  }

  void check() {
    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
      throw new CancellationException("图片采集已取消。");
    }
    if (System.nanoTime() - deadline >= 0) {
      throw new IllegalStateException(timeoutMessage);
    }
  }
}
