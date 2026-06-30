package com.github.quantumxiaol.craftmaid.intent;

import java.util.List;

public record MaidActionPlan(String chat, List<MaidAction> actions) {
  public static MaidActionPlan chat(String chat) {
    return new MaidActionPlan(chat == null ? "" : chat.trim(), List.of());
  }

  public boolean hasActions() {
    return actions != null && !actions.isEmpty();
  }
}
