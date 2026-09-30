package com.github.quantumxiaol.craftmaid.conversation;

import java.util.Base64;

/** An inline image for this request only. Never store it in conversation history. */
public record ConversationImage(String label, String dataUrl) {
  public static final int MAX_PNG_BYTES = 2 * 1024 * 1024;
  private static final String PREFIX = "data:image/png;base64,";

  public ConversationImage {
    if (label == null || label.isBlank() || label.length() > 120)
      throw new IllegalArgumentException("图片方向标签无效。");
    if (dataUrl == null
        || !dataUrl.startsWith(PREFIX)
        || dataUrl.length() <= PREFIX.length()
        || dataUrl.length() > PREFIX.length() + 4 * ((MAX_PNG_BYTES + 2) / 3))
      throw new IllegalArgumentException("图片必须是大小受限的 PNG data URL。");
  }

  public static ConversationImage png(String label, byte[] png) {
    if (png == null || png.length == 0 || png.length > MAX_PNG_BYTES)
      throw new IllegalArgumentException("单张图片超过 2 MiB 或为空。");
    return new ConversationImage(label, PREFIX + Base64.getEncoder().encodeToString(png));
  }

  @Override
  public String toString() {
    return "ConversationImage[label=" + label + ", dataUrl=<omitted>]";
  }
}
