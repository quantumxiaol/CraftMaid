package com.github.quantumxiaol.craftmaid.intent;

import java.util.List;

public record MaidActionExecutionResult(boolean executed, List<String> resultLines) {
  public String summary() {
    if (resultLines == null || resultLines.isEmpty()) {
      return "无动作结果。";
    }
    return String.join("\n", resultLines);
  }
}
