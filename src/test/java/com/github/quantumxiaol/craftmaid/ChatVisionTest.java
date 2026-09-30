package com.github.quantumxiaol.craftmaid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.IntentSettings;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.PerceptionSettings;
import com.github.quantumxiaol.craftmaid.context.MaidRuntimeContextCollector;
import com.github.quantumxiaol.craftmaid.control.MaidControlService;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.conversation.ConversationImage;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.github.quantumxiaol.craftmaid.intent.MaidActionExecutionResult;
import com.github.quantumxiaol.craftmaid.intent.MaidActionExecutor;
import com.github.quantumxiaol.craftmaid.llm.LlmClient;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import com.github.quantumxiaol.craftmaid.perception.MaidPerceptionService;
import com.github.quantumxiaol.craftmaid.vision.MaidVisionService;
import com.github.quantumxiaol.craftmaid.vision.VisionObservation;
import com.github.quantumxiaol.craftmaid.vision.VisionSettings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ChatVisionTest {
  private final CraftMaid plugin = mock(CraftMaid.class);
  private final Player player = mock(Player.class);
  private final LlmClient llm = mock(LlmClient.class);
  private final MaidVisionService vision = mock(MaidVisionService.class);
  private final MaidPerceptionService perception = mock(MaidPerceptionService.class);
  private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
  private final List<List<ConversationMessage>> finalMessages = new ArrayList<>();
  private final List<CompletableFuture<String>> replies = new ArrayList<>();
  private MockedStatic<Bukkit> bukkit;
  private MockedConstruction<MaidRuntimeContextCollector> contexts;
  private MockedConstruction<MaidActionExecutor> executors;
  private ChatListener listener;
  private Consumer<MaidVisionService.CaptureResult> captureCallback;

  @BeforeEach
  void setup() {
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("chat-vision-test"));
    when(plugin.getMaidName()).thenReturn("露西");
    when(plugin.getReplyPrefix()).thenReturn("{name}: ");
    when(plugin.getMaidNpcService()).thenReturn(mock(MaidNpcService.class));
    when(plugin.getMaidControlService()).thenReturn(new MaidControlService(plugin));
    when(plugin.getIntentSettings())
        .thenReturn(new IntentSettings(true, true, true, true, true, true, 500, .2, 500, .2));
    when(plugin.getVisionSettings())
        .thenReturn(new VisionSettings(true, 384, 256, 24, 100, 10, 10, 10, 10));
    when(plugin.getPerceptionSettings()).thenReturn(new PerceptionSettings(true, null, null, null));
    when(plugin.getPerceptionService()).thenReturn(perception);
    when(perception.inspectSurroundings(player)).thenReturn("女仆请求时的文字记录：旧位置有石头");
    when(plugin.getVisionService()).thenReturn(vision);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getName()).thenReturn("player");
    when(player.isOnline()).thenReturn(true);
    var history = mock(ConversationHistory.class);
    when(plugin.getConversationHistory()).thenReturn(history);
    when(history.buildPromptMessages(any(), anyString()))
        .thenAnswer(call -> List.of(ConversationMessage.user(call.getArgument(1))));
    when(history.buildPromptMessages(any(), anyString(), anyList()))
        .thenAnswer(
            call -> List.of(ConversationMessage.user(call.getArgument(1), call.getArgument(2))));
    when(llm.askJsonAsync(anyString(), anyList(), anyInt(), anyDouble(), anyBoolean(), eq("final")))
        .thenAnswer(
            call -> {
              finalMessages.add(List.copyOf(call.getArgument(1)));
              var reply = new CompletableFuture<String>();
              replies.add(reply);
              return reply;
            });
    when(vision.captureForLlm(any()))
        .thenAnswer(
            call -> {
              captureCallback = call.getArgument(0);
              return new MaidVisionService.StartResult(true, "started");
            });
    var scheduler = mock(BukkitScheduler.class);
    when(scheduler.runTask(eq(plugin), any(Runnable.class)))
        .thenAnswer(
            call -> {
              tasks.add(call.getArgument(1));
              return mock(BukkitTask.class);
            });
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    contexts = mockConstruction(MaidRuntimeContextCollector.class);
    executors = mockConstruction(MaidActionExecutor.class);
    listener = new ChatListener(plugin, llm);
  }

  @AfterEach
  void close() {
    executors.close();
    contexts.close();
    bukkit.close();
  }

  @Test
  void observationWaitsForFourImagesAndUsesOnlyTheirSnapshotText() throws Exception {
    plan("{\"chat\":\"\",\"actions\":[{\"type\":\"INSPECT_SURROUNDINGS\"}]}");
    assertTrue(finalMessages.isEmpty());
    verifyNoInteractions(executors.constructed().getFirst());
    when(perception.inspectSurroundings(player)).thenReturn("已经移动到新位置");
    completeCapture();
    var current = finalMessages.getFirst().getLast();
    assertEquals(4, current.images().size());
    assertTrue(current.content().contains("maid-snapshot-origin"));
    assertFalse(current.content().contains("旧位置有石头"));
    assertFalse(current.content().contains("已经移动"));
    verify(perception, times(1)).inspectSurroundings(player);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectedOrFailedCaptureUsesFrozenTextWithNoImages(boolean rejected) throws Exception {
    if (rejected)
      when(vision.captureForLlm(any())).thenReturn(new MaidVisionService.StartResult(false, "冷却中"));
    plan("{\"chat\":\"\",\"actions\":[{\"type\":\"INSPECT_SURROUNDINGS\"}]}");
    if (!rejected) {
      when(perception.inspectSurroundings(player)).thenReturn("已经移动到新位置");
      captureCallback.accept(new MaidVisionService.CaptureResult(false, null, "渲染超时"));
    }
    var current = finalMessages.getFirst().getLast();
    assertTrue(current.images().isEmpty());
    assertTrue(current.content().contains("本轮未提供图片"));
    assertTrue(current.content().contains("旧位置有石头"));
    assertFalse(current.content().contains("已经移动"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"reload", "stop", "offline"})
  void lateCaptureCannotSendAfterReloadStopOrDisconnect(String change) throws Exception {
    plan("{\"chat\":\"\",\"actions\":[{\"type\":\"INSPECT_SURROUNDINGS\"}]}");
    switch (change) {
      case "reload" -> listener.updateClient(mock(LlmClient.class));
      case "stop" -> plugin.getMaidControlService().stopFollowing();
      case "offline" -> when(player.isOnline()).thenReturn(false);
    }
    completeCapture();
    assertTrue(finalMessages.isEmpty());
  }

  @Test
  void rejectedImageInputRetriesTextOnceWithoutRecaptureOrActions() throws Exception {
    plan("{\"chat\":\"\",\"actions\":[{\"type\":\"INSPECT_SURROUNDINGS\"}]}");
    completeCapture();
    var rejection = new RuntimeException("image rejected");
    when(llm.isImageInputRejected(rejection)).thenReturn(true);
    replies.getFirst().completeExceptionally(rejection);
    drain();
    assertEquals(2, finalMessages.size());
    var retry = finalMessages.get(1).getLast();
    assertTrue(retry.images().isEmpty());
    assertTrue(retry.content().contains("maid-snapshot-origin"));
    assertTrue(retry.content().contains("本轮未提供图片"));
    replies.get(1).completeExceptionally(rejection);
    drain();
    assertEquals(2, finalMessages.size());
    verify(vision, times(1)).captureForLlm(any());
    verifyNoInteractions(executors.constructed().getFirst());
  }

  @Test
  void ordinaryChatDoesNotCaptureOrAttachImages() throws Exception {
    plan("{\"chat\":\"你好呀\",\"actions\":[]}");
    verifyNoInteractions(vision);
    assertTrue(finalMessages.isEmpty());
    verify(llm)
        .askJsonAsync(
            anyString(),
            argThat(messages -> messages.stream().allMatch(message -> message.images().isEmpty())),
            anyInt(),
            anyDouble(),
            anyBoolean(),
            eq("plan"));
  }

  @Test
  void sendingSwitchKeepsTextObservationAvailable() throws Exception {
    when(plugin.getVisionSettings())
        .thenReturn(new VisionSettings(true, 384, 256, 24, 100, 10, 10, 10, 10, false));
    when(executors.constructed().getFirst().execute(eq(player), any()))
        .thenReturn(new MaidActionExecutionResult(true, List.of("文字观察")));
    plan("{\"chat\":\"\",\"actions\":[{\"type\":\"INSPECT_SURROUNDINGS\"}]}");
    verifyNoInteractions(vision);
    assertTrue(finalMessages.getFirst().getLast().images().isEmpty());
  }

  private void plan(String json) throws Exception {
    when(llm.askJsonAsync(anyString(), anyList(), anyInt(), anyDouble(), anyBoolean(), eq("plan")))
        .thenReturn(CompletableFuture.completedFuture(json));
    var method =
        ChatListener.class.getDeclaredMethod(
            "handleJsonTurn",
            Player.class,
            String.class,
            LlmClient.class,
            long.class,
            boolean.class);
    method.setAccessible(true);
    method.invoke(listener, player, "看看周围有什么", llm, 0L, true);
    drain();
  }

  private void completeCapture() {
    var images =
        List.of("NORTH", "EAST", "SOUTH", "WEST").stream()
            .map(label -> ConversationImage.png(label, new byte[] {1, 2, 3}))
            .toList();
    captureCallback.accept(
        new MaidVisionService.CaptureResult(
            true, null, "saved", new VisionObservation("maid-snapshot-origin", images)));
  }

  private void drain() {
    while (!tasks.isEmpty()) tasks.remove().run();
  }
}
