package com.github.quantumxiaol.craftmaid.conversation;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ConversationHistory {
  private final CraftMaid plugin;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final Map<UUID, ArrayDeque<ConversationMessage>> histories = new HashMap<>();
  private final Map<UUID, String> memorySummaries = new HashMap<>();
  private final Map<UUID, String> playerNames = new HashMap<>();
  private final Set<UUID> compressingPlayers = new HashSet<>();

  private boolean enabled = true;
  private boolean persistEnabled;
  private boolean loadedFromDisk;
  private int maxMessages = 100;
  private int maxMessageChars = 1200;
  private int maxMemoryChars = 4000;
  private Path persistPath;

  public ConversationHistory(CraftMaid plugin) {
    this.plugin = plugin;
    this.persistPath = plugin.getDataFolder().toPath().resolve("conversations.json");
  }

  public synchronized void configure(
      boolean enabled,
      int maxMessages,
      int maxMessageChars,
      int maxMemoryChars,
      boolean persistEnabled,
      String persistFile) {
    this.enabled = enabled;
    this.maxMessages = Math.max(1, maxMessages);
    this.maxMessageChars = Math.max(80, maxMessageChars);
    this.maxMemoryChars = Math.max(400, maxMemoryChars);
    this.persistEnabled = persistEnabled;
    this.persistPath =
        plugin
            .getDataFolder()
            .toPath()
            .resolve(
                persistFile == null || persistFile.isBlank() ? "conversations.json" : persistFile);

    if (persistEnabled && !loadedFromDisk) {
      load();
    }
  }

  public synchronized List<ConversationMessage> buildPromptMessages(
      UUID playerId, String currentUserPrompt) {
    ConversationMessage currentMessage = ConversationMessage.user(trimContent(currentUserPrompt));
    if (!enabled) {
      return List.of(currentMessage);
    }

    ArrayDeque<ConversationMessage> history = histories.get(playerId);
    String memorySummary = memorySummaries.get(playerId);
    if ((memorySummary == null || memorySummary.isBlank())
        && (history == null || history.isEmpty())) {
      return List.of(currentMessage);
    }

    List<ConversationMessage> messages = new ArrayList<>();
    if (memorySummary != null && !memorySummary.isBlank()) {
      messages.add(ConversationMessage.system(formatMemorySummary(memorySummary)));
    }

    List<ConversationMessage> stored = history == null ? List.of() : new ArrayList<>(history);
    int previousLimit = Math.max(0, maxMessages - 1);
    int fromIndex = Math.max(0, stored.size() - previousLimit);

    messages.addAll(stored.subList(fromIndex, stored.size()));
    messages.add(currentMessage);
    return messages;
  }

  public synchronized void appendExchange(
      UUID playerId, String playerName, String userPrompt, String assistantReply) {
    if (!enabled) {
      return;
    }

    ArrayDeque<ConversationMessage> history =
        histories.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
    addMessage(history, ConversationMessage.user(userPrompt));
    addMessage(history, ConversationMessage.assistant(assistantReply));
    playerNames.put(playerId, playerName);

    save();
  }

  public synchronized CompressionRequest prepareCompression(UUID playerId) {
    if (!enabled || compressingPlayers.contains(playerId)) {
      return null;
    }

    ArrayDeque<ConversationMessage> history = histories.get(playerId);
    if (history == null || history.size() <= maxMessages) {
      return null;
    }

    int recentMessages = Math.max(1, maxMessages / 5);
    int oldMessageCount = Math.max(0, history.size() - recentMessages);
    if (oldMessageCount <= 0) {
      return null;
    }

    List<ConversationMessage> oldMessages = new ArrayList<>(history).subList(0, oldMessageCount);
    compressingPlayers.add(playerId);
    return new CompressionRequest(
        playerId,
        playerNames.getOrDefault(playerId, playerId.toString()),
        memorySummaries.getOrDefault(playerId, ""),
        new ArrayList<>(oldMessages),
        oldMessageCount,
        recentMessages);
  }

  public synchronized void applyCompression(CompressionRequest request, String memorySummary) {
    if (!compressingPlayers.remove(request.playerId())) {
      return;
    }

    String trimmedSummary = trimMemory(memorySummary);
    if (trimmedSummary.isBlank()) {
      return;
    }

    ArrayDeque<ConversationMessage> history = histories.get(request.playerId());
    if (history == null || history.isEmpty()) {
      memorySummaries.put(request.playerId(), trimmedSummary);
      playerNames.put(request.playerId(), request.playerName());
      save();
      return;
    }

    int messagesToRemove = Math.min(request.oldMessageCount(), history.size());
    for (int i = 0; i < messagesToRemove; i++) {
      history.removeFirst();
    }
    memorySummaries.put(request.playerId(), trimmedSummary);
    playerNames.put(request.playerId(), request.playerName());
    save();
  }

  public synchronized void cancelCompression(UUID playerId) {
    compressingPlayers.remove(playerId);
  }

  public synchronized boolean clear(UUID playerId) {
    boolean removed = histories.remove(playerId) != null;
    removed = memorySummaries.remove(playerId) != null || removed;
    playerNames.remove(playerId);
    compressingPlayers.remove(playerId);
    if (removed) {
      save();
    }
    return removed;
  }

  public synchronized boolean clearByPlayerName(String playerName) {
    if (playerName == null || playerName.isBlank()) {
      return false;
    }

    String normalized = playerName.toLowerCase(Locale.ROOT);
    UUID matchedId = null;
    for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
      if (entry.getValue() != null
          && entry.getValue().toLowerCase(Locale.ROOT).equals(normalized)) {
        matchedId = entry.getKey();
        break;
      }
    }

    if (matchedId == null) {
      return false;
    }
    return clear(matchedId);
  }

  public synchronized int clearAll() {
    Set<UUID> playerIds = new HashSet<>();
    playerIds.addAll(histories.keySet());
    playerIds.addAll(memorySummaries.keySet());
    int count = playerIds.size();
    histories.clear();
    memorySummaries.clear();
    playerNames.clear();
    compressingPlayers.clear();
    if (count > 0) {
      save();
    }
    return count;
  }

  public synchronized int getHistorySize(UUID playerId) {
    ArrayDeque<ConversationMessage> history = histories.get(playerId);
    return history == null ? 0 : history.size();
  }

  public synchronized void save() {
    if (!persistEnabled) {
      return;
    }

    try {
      Files.createDirectories(persistPath.getParent());
      Path tempPath = persistPath.resolveSibling(persistPath.getFileName() + ".tmp");
      Files.writeString(tempPath, gson.toJson(toJson()), StandardCharsets.UTF_8);
      Files.move(
          tempPath,
          persistPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException ex) {
      plugin.getLogger().warning("保存对话历史失败: " + ex.getMessage());
    }
  }

  private void load() {
    loadedFromDisk = true;
    if (!Files.isRegularFile(persistPath)) {
      return;
    }

    try {
      JsonObject root =
          JsonParser.parseString(Files.readString(persistPath, StandardCharsets.UTF_8))
              .getAsJsonObject();
      JsonArray conversations = root.getAsJsonArray("conversations");
      if (conversations == null) {
        return;
      }

      histories.clear();
      memorySummaries.clear();
      playerNames.clear();
      for (JsonElement conversationElement : conversations) {
        if (!conversationElement.isJsonObject()) {
          continue;
        }

        JsonObject conversation = conversationElement.getAsJsonObject();
        UUID playerId = UUID.fromString(conversation.get("player_uuid").getAsString());
        String playerName =
            conversation.has("player_name")
                ? conversation.get("player_name").getAsString()
                : playerId.toString();
        String memorySummary =
            conversation.has("memory_summary")
                ? trimMemory(conversation.get("memory_summary").getAsString())
                : "";
        if (!memorySummary.isBlank()) {
          memorySummaries.put(playerId, memorySummary);
          playerNames.put(playerId, playerName);
        }

        JsonArray messages = conversation.getAsJsonArray("messages");
        if (messages == null) {
          continue;
        }

        ArrayDeque<ConversationMessage> history = new ArrayDeque<>();
        for (JsonElement messageElement : messages) {
          if (!messageElement.isJsonObject()) {
            continue;
          }
          JsonObject message = messageElement.getAsJsonObject();
          ConversationMessage conversationMessage =
              new ConversationMessage(
                  message.get("role").getAsString(), message.get("content").getAsString());
          addMessage(history, conversationMessage);
        }

        if (!history.isEmpty()) {
          histories.put(playerId, history);
          playerNames.put(playerId, playerName);
        }
      }
    } catch (RuntimeException | IOException ex) {
      plugin.getLogger().warning("读取对话历史失败，将使用空历史: " + ex.getMessage());
      histories.clear();
      memorySummaries.clear();
      playerNames.clear();
    }
  }

  private JsonObject toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);

    Set<UUID> playerIds = new HashSet<>();
    playerIds.addAll(histories.keySet());
    playerIds.addAll(memorySummaries.keySet());

    JsonArray conversations = new JsonArray();
    for (UUID playerId : playerIds) {
      JsonObject conversation = new JsonObject();
      conversation.addProperty("player_uuid", playerId.toString());
      conversation.addProperty(
          "player_name", playerNames.getOrDefault(playerId, playerId.toString()));
      conversation.addProperty("memory_summary", memorySummaries.getOrDefault(playerId, ""));

      JsonArray messages = new JsonArray();
      ArrayDeque<ConversationMessage> history =
          histories.getOrDefault(playerId, new ArrayDeque<>());
      for (ConversationMessage message : history) {
        JsonObject messageJson = new JsonObject();
        messageJson.addProperty("role", message.role());
        messageJson.addProperty("content", message.content());
        messages.add(messageJson);
      }
      conversation.add("messages", messages);
      conversations.add(conversation);
    }

    root.add("conversations", conversations);
    return root;
  }

  private void addMessage(ArrayDeque<ConversationMessage> history, ConversationMessage message) {
    if (message == null || !message.isValid()) {
      return;
    }
    history.addLast(new ConversationMessage(message.role(), trimContent(message.content())));
  }

  private String formatMemorySummary(String memorySummary) {
    return "以下是你与该玩家更早之前的长期记忆。继续遵守当前 system_prompt；如果这里的记忆与当前人设或规则冲突，以当前 system_prompt 为准。\n"
        + memorySummary;
  }

  private String trimMemory(String memory) {
    if (memory == null) {
      return "";
    }
    String trimmed = memory.trim();
    if (trimmed.length() <= maxMemoryChars) {
      return trimmed;
    }
    return "（更早的记忆过长，已保留较新的压缩片段。）\n" + trimmed.substring(trimmed.length() - maxMemoryChars);
  }

  private String trimContent(String content) {
    if (content == null) {
      return "";
    }
    String trimmed = content.trim();
    if (trimmed.length() <= maxMessageChars) {
      return trimmed;
    }
    return trimmed.substring(0, maxMessageChars) + "...";
  }

  public record CompressionRequest(
      UUID playerId,
      String playerName,
      String existingMemory,
      List<ConversationMessage> oldMessages,
      int oldMessageCount,
      int recentMessages) {
    public String toPrompt() {
      StringBuilder builder = new StringBuilder();
      builder.append("玩家名：").append(playerName).append("\n\n");
      builder.append("已有长期 Memory：\n");
      builder
          .append(existingMemory == null || existingMemory.isBlank() ? "暂无" : existingMemory)
          .append("\n\n");
      builder.append("最近 ").append(recentMessages).append(" 条原始对话会继续保留，不需要在摘要里猜测或重复它们。\n\n");
      builder.append("待压缩的旧对话：\n");
      for (ConversationMessage message : oldMessages) {
        builder
            .append(roleLabel(message.role()))
            .append(": ")
            .append(message.content())
            .append("\n");
      }
      return builder.toString();
    }

    private static String roleLabel(String role) {
      return switch (role) {
        case "assistant" -> "女仆";
        case "system" -> "系统记忆";
        default -> "玩家";
      };
    }
  }
}
