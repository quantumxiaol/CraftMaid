package com.github.quantumxiaol.craftmaid.intent;

public record MaidIntentResult(boolean matched, boolean consumed, boolean success, String message) {
  public static MaidIntentResult notMatched() {
    return new MaidIntentResult(false, false, false, "");
  }

  public static MaidIntentResult consumed(boolean success, String message) {
    return new MaidIntentResult(true, true, success, message);
  }
}
