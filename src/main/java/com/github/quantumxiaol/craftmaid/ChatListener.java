package com.github.quantumxiaol.craftmaid;

import com.github.quantumxiaol.craftmaid.context.WorldContextCollector;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.github.quantumxiaol.craftmaid.intent.MaidIntent;
import com.github.quantumxiaol.craftmaid.intent.MaidIntentDetector;
import com.github.quantumxiaol.craftmaid.intent.MaidIntentExecutor;
import com.github.quantumxiaol.craftmaid.intent.MaidIntentResult;
import com.github.quantumxiaol.craftmaid.llm.LlmClient;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {
  private final CraftMaid plugin;
  private final WorldContextCollector worldContextCollector = new WorldContextCollector();
  private final MaidIntentDetector intentDetector = new MaidIntentDetector();
  private final MaidIntentExecutor intentExecutor;
  private final Map<UUID, Long> nextAllowedReplyAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> conversationActiveUntil = new ConcurrentHashMap<>();
  private final Set<UUID> respondingPlayers = ConcurrentHashMap.newKeySet();
  private volatile LlmClient llmClient;

  public ChatListener(CraftMaid plugin, LlmClient llmClient) {
    this.plugin = plugin;
    this.llmClient = llmClient;
    this.intentExecutor = new MaidIntentExecutor(plugin);
  }

  public void updateClient(LlmClient llmClient) {
    this.llmClient = llmClient;
  }

  @EventHandler
  public void onPlayerChat(AsyncChatEvent event) {
    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

    String maidName = plugin.getMaidName();
    boolean addressedByName = containsMaidName(rawMessage, maidName);
    boolean inFollowupWindow = isConversationActive(playerId);

    if (!addressedByName && !inFollowupWindow) {
      return;
    }

    refreshConversationWindow(playerId);

    LlmClient client = this.llmClient;
    if (client == null) {
      plugin.getLogger().warning("LLM 客户端未初始化，已跳过本次回复。");
      return;
    }

    Bukkit.getScheduler()
        .runTask(
            plugin, () -> handleMaidMention(player, rawMessage, maidName, addressedByName, client));
  }

  private void handleMaidMention(
      Player player,
      String rawMessage,
      String maidName,
      boolean addressedByName,
      LlmClient client) {
    if (!player.isOnline()) {
      return;
    }

    String playerSpeech = addressedByName ? stripMaidName(rawMessage, maidName) : rawMessage.trim();
    if (tryHandleJobIntent(player, playerSpeech, addressedByName)) {
      return;
    }

    if (isCoolingDown(player.getUniqueId())) {
      return;
    }

    UUID playerId = player.getUniqueId();
    String playerName = player.getName();
    if (!respondingPlayers.add(playerId)) {
      player.sendMessage(Component.text(maidName + " 还在思考刚才的问题，请稍等一下。", NamedTextColor.YELLOW));
      return;
    }

    String masterName = plugin.getMasterName();
    String environmentStr =
        worldContextCollector.collectForPrompt(player, plugin.getMaxContextEntities());
    String jobStatus = plugin.getJobService().statusLine();

    boolean isMaster = player.getName().equalsIgnoreCase(masterName);
    String identityStr = isMaster ? "主人" : "其他玩家";

    String systemPrompt = plugin.getSystemPrompt();
    if (playerSpeech.isBlank()) {
      playerSpeech = "正在呼唤你，请自然回应。";
    }

    String userPrompt =
        String.format(
            "当前环境：%s 当前女仆工作状态：%s 跟我说话的人是 %s (%s) 对我说：“%s”",
            environmentStr, jobStatus, playerName, identityStr, playerSpeech);
    List<ConversationMessage> conversationMessages =
        plugin.getConversationHistory().buildPromptMessages(playerId, userPrompt);

    client
        .askAiAsync(systemPrompt, conversationMessages)
        .whenComplete(
            (reply, ex) -> {
              respondingPlayers.remove(playerId);
              if (!plugin.isEnabled()) {
                return;
              }

              if (ex != null) {
                plugin.getLogger().warning("请求 AI 失败: " + rootMessage(ex));
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (player.isOnline()) {
                            player.sendMessage(
                                Component.text(maidName + " 暂时没有回应，请稍后再试。", NamedTextColor.RED));
                          }
                        });
                return;
              }

              String cleanReply = reply == null ? "" : reply.trim();
              if (cleanReply.isBlank()) {
                return;
              }

              plugin
                  .getConversationHistory()
                  .appendExchange(playerId, playerName, userPrompt, cleanReply);
              triggerMemoryCompression(playerId, client);
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (!plugin.isEnabled()) {
                          return;
                        }
                        String prefix = plugin.getReplyPrefix().replace("{name}", maidName);
                        Component replyComponent =
                            Component.text(prefix + cleanReply, NamedTextColor.LIGHT_PURPLE);
                        Bukkit.broadcast(replyComponent);
                      });
            });
  }

  private boolean tryHandleJobIntent(Player player, String playerSpeech, boolean addressedByName) {
    if (!plugin.getIntentSettings().enabled()) {
      return false;
    }
    if (!addressedByName && !plugin.getIntentSettings().allowFollowupWindow()) {
      return false;
    }

    Optional<MaidIntent> intent = intentDetector.detect(playerSpeech);
    if (intent.isEmpty()) {
      return false;
    }
    MaidIntentResult result = intentExecutor.execute(player, intent.get());
    if (!result.message().isBlank()) {
      player.sendMessage(
          Component.text(
              result.message(),
              result.success() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.RED));
    }
    return result.consumed();
  }

  private void triggerMemoryCompression(UUID playerId, LlmClient client) {
    ConversationHistory.CompressionRequest compressionRequest =
        plugin.getConversationHistory().prepareCompression(playerId);
    if (compressionRequest == null) {
      return;
    }

    client
        .summarizeMemoryAsync(
            compressionRequest,
            plugin.getConversationSummaryMaxTokens(),
            plugin.getConversationSummaryTemperature())
        .whenComplete(
            (memorySummary, ex) -> {
              if (!plugin.isEnabled()) {
                plugin.getConversationHistory().cancelCompression(playerId);
                return;
              }

              if (ex != null) {
                plugin.getConversationHistory().cancelCompression(playerId);
                plugin.getLogger().warning("压缩对话历史失败，已保留原始历史: " + rootMessage(ex));
                return;
              }

              plugin.getConversationHistory().applyCompression(compressionRequest, memorySummary);
            });
  }

  private boolean isConversationActive(UUID playerId) {
    int followupSeconds = plugin.getChatFollowupSeconds();
    if (followupSeconds <= 0) {
      return false;
    }

    Long activeUntil = conversationActiveUntil.get(playerId);
    long now = System.currentTimeMillis();
    if (activeUntil == null) {
      return false;
    }
    if (activeUntil < now) {
      conversationActiveUntil.remove(playerId, activeUntil);
      return false;
    }
    return true;
  }

  private void refreshConversationWindow(UUID playerId) {
    int followupSeconds = plugin.getChatFollowupSeconds();
    if (followupSeconds <= 0) {
      conversationActiveUntil.remove(playerId);
      return;
    }
    conversationActiveUntil.put(playerId, System.currentTimeMillis() + followupSeconds * 1000L);
  }

  private boolean containsMaidName(String rawMessage, String maidName) {
    if (rawMessage == null || maidName == null || maidName.isBlank()) {
      return false;
    }
    return rawMessage.toLowerCase(Locale.ROOT).contains(maidName.toLowerCase(Locale.ROOT));
  }

  private String stripMaidName(String rawMessage, String maidName) {
    return Pattern.compile(Pattern.quote(maidName), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(rawMessage)
        .replaceAll("")
        .trim();
  }

  private boolean isCoolingDown(UUID playerId) {
    int cooldownSeconds = plugin.getChatCooldownSeconds();
    if (cooldownSeconds <= 0) {
      return false;
    }

    long now = System.currentTimeMillis();
    Long nextAllowed = nextAllowedReplyAt.get(playerId);
    if (nextAllowed != null && now < nextAllowed) {
      return true;
    }

    nextAllowedReplyAt.put(playerId, now + cooldownSeconds * 1000L);
    return false;
  }

  private String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
  }
}
