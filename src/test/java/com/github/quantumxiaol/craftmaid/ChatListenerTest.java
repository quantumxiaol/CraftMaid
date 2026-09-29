package com.github.quantumxiaol.craftmaid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.IntentSettings;
import com.github.quantumxiaol.craftmaid.context.MaidRuntimeContextCollector;
import com.github.quantumxiaol.craftmaid.control.MaidControlService;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.intent.MaidActionExecutor;
import com.github.quantumxiaol.craftmaid.llm.LlmClient;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import com.github.quantumxiaol.craftmaid.perception.MaidPerceptionService;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatListenerTest {
  @ParameterizedTest
  @CsvSource({"true,true", "false,false", "false,true"})
  void pendingPlanChecksControlRevisionOnTheMainThread(boolean stopped, boolean allowActions)
      throws Exception {
    CraftMaid plugin = mock(CraftMaid.class);
    MaidNpcService npc = mock(MaidNpcService.class);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    var control = new MaidControlService(plugin);
    when(plugin.getMaidControlService()).thenReturn(control);
    when(plugin.getIntentSettings())
        .thenReturn(new IntentSettings(true, true, true, true, true, true, 500, .2, 500, .2));
    var history = mock(ConversationHistory.class);
    when(plugin.getConversationHistory()).thenReturn(history);
    when(history.buildPromptMessages(any(), anyString())).thenReturn(List.of());
    when(plugin.getPerceptionService()).thenReturn(mock(MaidPerceptionService.class));
    var player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    var llm = mock(LlmClient.class);
    var pendingPlan = new CompletableFuture<String>();
    when(llm.askJsonAsync(anyString(), anyList(), anyInt(), anyDouble(), anyBoolean(), eq("plan")))
        .thenReturn(pendingPlan);
    var scheduler = mock(BukkitScheduler.class);
    var queue = new ArrayDeque<Runnable>();
    when(scheduler.runTask(eq(plugin), any(Runnable.class)))
        .thenAnswer(
            call -> {
              queue.add(call.getArgument(1));
              return mock(BukkitTask.class);
            });
    try (var bukkit = mockStatic(Bukkit.class);
        var context = mockConstruction(MaidRuntimeContextCollector.class);
        var executors = mockConstruction(MaidActionExecutor.class)) {
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      var listener = new ChatListener(plugin, llm);
      var handle =
          ChatListener.class.getDeclaredMethod(
              "handleJsonTurn",
              Player.class,
              String.class,
              LlmClient.class,
              long.class,
              boolean.class);
      handle.setAccessible(true);
      handle.invoke(listener, player, "去钓鱼", llm, 0L, allowActions);
      pendingPlan.complete("{\"chat\":\"\",\"actions\":[{\"type\":\"FISHING_START\"}]}");
      assertEquals(1, queue.size());
      // Stop can arrive after the HTTP response but before the queued Bukkit callback.
      if (stopped) control.stopFollowing();
      if (!stopped && allowActions) {
        when(executors.constructed().getFirst().execute(any(), any()))
            .thenReturn(
                new com.github.quantumxiaol.craftmaid.intent.MaidActionExecutionResult(
                    true, List.of("ok")));
        when(llm.askJsonAsync(
                anyString(), anyList(), anyInt(), anyDouble(), anyBoolean(), eq("final")))
            .thenReturn(new CompletableFuture<>());
      }
      queue.remove().run();
      if (stopped || !allowActions) verifyNoInteractions(executors.constructed().getFirst());
      else verify(executors.constructed().getFirst()).execute(eq(player), any());
    }
  }
}
