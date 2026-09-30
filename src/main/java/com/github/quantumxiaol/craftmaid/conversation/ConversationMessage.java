package com.github.quantumxiaol.craftmaid.conversation;

import java.util.List;

public record ConversationMessage(String role, String content, List<ConversationImage> images) {
  public ConversationMessage {
    images = images == null ? List.of() : List.copyOf(images);
    if (images.size() > 4 || (!images.isEmpty() && !"user".equals(role)))
      throw new IllegalArgumentException("只有当前 user 消息可以携带最多四张图片。");
  }

  public ConversationMessage(String role, String content) {
    this(role, content, List.of());
  }

  public static ConversationMessage user(String content, List<ConversationImage> images) {
    return new ConversationMessage("user", content, images);
  }

  public static ConversationMessage user(String content) {
    return new ConversationMessage("user", content);
  }

  public static ConversationMessage assistant(String content) {
    return new ConversationMessage("assistant", content);
  }

  public static ConversationMessage system(String content) {
    return new ConversationMessage("system", content);
  }

  public boolean isValid() {
    return ("system".equals(role) || "user".equals(role) || "assistant".equals(role))
        && content != null
        && !content.isBlank();
  }
}
