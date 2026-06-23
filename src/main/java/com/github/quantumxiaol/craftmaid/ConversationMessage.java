package com.github.quantumxiaol.craftmaid;

public record ConversationMessage(String role, String content) {
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
