package com.github.quantumxiaol.craftmaid.conversation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversationHistoryTest {
  @TempDir Path directory;

  @Test
  void imagesBelongOnlyToCurrentPromptAndNeverPersist() throws Exception {
    CraftMaid plugin = mock(CraftMaid.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    var history = new ConversationHistory(plugin);
    history.configure(true, 100, 1200, 4000, true, "history.json");
    UUID player = UUID.randomUUID();
    var image = ConversationImage.png("NORTH", new byte[] {1, 2, 3});
    var current = history.buildPromptMessages(player, "同一快照", java.util.List.of(image));
    assertEquals(java.util.List.of(image), current.getLast().images());
    history.appendExchange(player, "player", "附近有什么", "北边有树");
    var next = history.buildPromptMessages(player, "普通聊天");
    assertEquals(3, next.size());
    assertTrue(next.stream().allMatch(message -> message.images().isEmpty()));
    history.shutdown();
    String saved = java.nio.file.Files.readString(directory.resolve("history.json"));
    assertFalse(saved.contains("base64"));
    assertFalse(saved.contains("同一快照"));
    assertTrue(saved.contains("北边有树"));
  }

  @Test
  void shutdownFlushesBatchedHistoryAndForget() throws Exception {
    CraftMaid plugin = mock(CraftMaid.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    var history = new ConversationHistory(plugin);
    history.configure(true, 100, 1200, 4000, true, "history.json");
    UUID playerId = UUID.randomUUID();
    history.appendExchange(playerId, "player", "hello", "world");
    history.appendExchange(playerId, "player", "second", "reply");
    history.shutdown();
    var reloaded = new ConversationHistory(plugin);
    reloaded.configure(true, 100, 1200, 4000, true, "history.json");
    assertEquals(4, reloaded.getHistorySize(playerId));
    reloaded.clearAll();
    reloaded.shutdown();
    var cleared = new ConversationHistory(plugin);
    cleared.configure(true, 100, 1200, 4000, true, "history.json");
    assertEquals(0, cleared.getHistorySize(playerId));
    cleared.shutdown();
  }

  @Test
  void limitsPlayerSpeechButPreservesComposedActionResults() {
    CraftMaid plugin = mock(CraftMaid.class);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    ConversationHistory history = new ConversationHistory(plugin);
    history.configure(true, 100, 1200, 4000, false, "history.json");
    String speech = history.limitPlayerSpeech("x".repeat(1500));
    assertEquals(1203, speech.length());
    String prompt = speech + "\n环境：" + "水".repeat(2000) + "\n动作结果：failure\nactions=[]";
    assertEquals(
        prompt, history.buildPromptMessages(UUID.randomUUID(), prompt).getLast().content());
    history.configure(false, 100, 1200, 4000, false, "history.json");
    assertEquals(
        prompt, history.buildPromptMessages(UUID.randomUUID(), prompt).getLast().content());
  }
}
