package com.github.quantumxiaol.craftmaid;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.IntentSettings;
import com.github.quantumxiaol.craftmaid.context.MaidRuntimeContextCollector;
import com.github.quantumxiaol.craftmaid.context.WorldContextCollector;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.github.quantumxiaol.craftmaid.intent.MaidActionExecutionResult;
import com.github.quantumxiaol.craftmaid.intent.MaidActionExecutor;
import com.github.quantumxiaol.craftmaid.intent.MaidActionPlan;
import com.github.quantumxiaol.craftmaid.intent.MaidActionPlanParser;
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
  private final MaidRuntimeContextCollector runtimeContextCollector;
  private final MaidActionPlanParser actionPlanParser = new MaidActionPlanParser();
  private final MaidActionExecutor actionExecutor;
  private final MaidIntentDetector intentDetector = new MaidIntentDetector();
  private final MaidIntentExecutor intentExecutor;
  private final Map<UUID, Long> nextAllowedReplyAt = new ConcurrentHashMap<>();
  private final Map<UUID, Long> conversationActiveUntil = new ConcurrentHashMap<>();
  private final Set<UUID> respondingPlayers = ConcurrentHashMap.newKeySet();
  private volatile LlmClient llmClient;

  public ChatListener(CraftMaid plugin, LlmClient llmClient) {
    this.plugin = plugin;
    this.llmClient = llmClient;
    this.runtimeContextCollector = new MaidRuntimeContextCollector(plugin);
    this.actionExecutor = new MaidActionExecutor(plugin);
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
    IntentSettings intentSettings = plugin.getIntentSettings();
    boolean useJsonTurn = intentSettings.enabled() && intentSettings.llmJson();
    if (!useJsonTurn && tryHandleFallbackIntent(player, playerSpeech, addressedByName)) {
      return;
    }

    if (!useJsonTurn && isCoolingDown(player.getUniqueId())) {
      return;
    }

    UUID playerId = player.getUniqueId();
    String playerName = player.getName();
    if (!respondingPlayers.add(playerId)) {
      player.sendMessage(Component.text(maidName + " 还在思考刚才的问题，请稍等一下。", NamedTextColor.YELLOW));
      return;
    }

    if (playerSpeech.isBlank()) {
      playerSpeech = "正在呼唤你，请自然回应。";
    }
    String turnPlayerSpeech = playerSpeech;

    if (useJsonTurn) {
      handleJsonTurn(player, turnPlayerSpeech, client);
      return;
    }

    String userPrompt = buildPlainChatPrompt(player, turnPlayerSpeech);
    List<ConversationMessage> conversationMessages =
        plugin.getConversationHistory().buildPromptMessages(playerId, userPrompt);
    String systemPrompt = plugin.getSystemPrompt();

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
                  .appendExchange(playerId, playerName, turnPlayerSpeech, cleanReply);
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

  private void handleJsonTurn(Player player, String playerSpeech, LlmClient client) {
    UUID playerId = player.getUniqueId();
    String playerName = player.getName();
    IntentSettings settings = plugin.getIntentSettings();
    String planPrompt = buildPlanPrompt(player, playerSpeech);
    List<ConversationMessage> conversationMessages =
        plugin.getConversationHistory().buildPromptMessages(playerId, planPrompt);

    client
        .askJsonAsync(
            buildJsonTurnSystemPrompt(),
            conversationMessages,
            settings.planMaxTokens(),
            settings.planTemperature(),
            settings.responseFormatJsonObject(),
            "plan")
        .whenComplete(
            (rawPlan, ex) -> {
              if (ex != null) {
                failTurn(player, playerId, "请求动作计划失败: " + rootMessage(ex));
                return;
              }

              Optional<MaidActionPlan> plan = actionPlanParser.parse(rawPlan);
              if (plan.isEmpty()) {
                failTurn(player, playerId, "女仆没有按 JSON 格式回应，请再说一次。");
                return;
              }

              MaidActionPlan actionPlan = plan.get();
              if (!actionPlan.hasActions()) {
                String chat = actionPlan.chat() == null ? "" : actionPlan.chat().trim();
                if (chat.isBlank()) {
                  failTurn(player, playerId, "女仆没有想好该怎么回应，请再说一次。");
                  return;
                }
                finishTurn(player, playerId, playerName, playerSpeech, chat, client);
                return;
              }

              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (!player.isOnline()) {
                          respondingPlayers.remove(playerId);
                          return;
                        }
                        MaidActionExecutionResult actionResult =
                            actionExecutor.execute(player, actionPlan);
                        requestFinalReply(
                            player, playerId, playerName, playerSpeech, actionResult, client);
                      });
            });
  }

  private void requestFinalReply(
      Player player,
      UUID playerId,
      String playerName,
      String playerSpeech,
      MaidActionExecutionResult actionResult,
      LlmClient client) {
    IntentSettings settings = plugin.getIntentSettings();
    String finalPrompt = buildFinalPrompt(player, playerSpeech, actionResult);
    List<ConversationMessage> conversationMessages =
        plugin.getConversationHistory().buildPromptMessages(playerId, finalPrompt);
    client
        .askJsonAsync(
            buildJsonTurnSystemPrompt(),
            conversationMessages,
            settings.finalMaxTokens(),
            settings.finalTemperature(),
            settings.responseFormatJsonObject(),
            "final")
        .whenComplete(
            (rawFinal, ex) -> {
              if (ex != null) {
                String fallback = fallbackFinalReply(actionResult);
                plugin.getLogger().warning("请求动作结果回复失败: " + rootMessage(ex));
                finishTurn(player, playerId, playerName, playerSpeech, fallback, client);
                return;
              }

              String finalChat =
                  actionPlanParser.parse(rawFinal).map(MaidActionPlan::chat).orElse("").trim();
              if (finalChat.isBlank()) {
                finalChat = fallbackFinalReply(actionResult);
              }
              finishTurn(player, playerId, playerName, playerSpeech, finalChat, client);
            });
  }

  private void finishTurn(
      Player player,
      UUID playerId,
      String playerName,
      String playerSpeech,
      String cleanReply,
      LlmClient client) {
    respondingPlayers.remove(playerId);
    if (!plugin.isEnabled()) {
      return;
    }
    String reply = cleanReply == null ? "" : cleanReply.trim();
    if (reply.isBlank()) {
      return;
    }

    plugin.getConversationHistory().appendExchange(playerId, playerName, playerSpeech, reply);
    triggerMemoryCompression(playerId, client);
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!plugin.isEnabled() || !player.isOnline()) {
                return;
              }
              String prefix = plugin.getReplyPrefix().replace("{name}", plugin.getMaidName());
              Bukkit.broadcast(Component.text(prefix + reply, NamedTextColor.LIGHT_PURPLE));
            });
  }

  private void failTurn(Player player, UUID playerId, String message) {
    respondingPlayers.remove(playerId);
    plugin.getLogger().warning(message);
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (player.isOnline()) {
                player.sendMessage(
                    Component.text(plugin.getMaidName() + " 暂时没听清，请再说一次。", NamedTextColor.RED));
              }
            });
  }

  private boolean tryHandleFallbackIntent(
      Player player, String playerSpeech, boolean addressedByName) {
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

  private String buildPlainChatPrompt(Player player, String playerSpeech) {
    String masterName = plugin.getMasterName();
    String environmentStr =
        worldContextCollector.collectForPrompt(player, plugin.getMaxContextEntities());
    boolean isMaster = player.getName().equalsIgnoreCase(masterName);
    String identityStr = isMaster ? "主人" : "其他玩家";
    return String.format(
        "当前环境：%s\n%s\n跟我说话的人是 %s (%s) 对我说：“%s”",
        environmentStr,
        runtimeContextCollector.collect(player),
        player.getName(),
        identityStr,
        playerSpeech);
  }

  private String buildPlanPrompt(Player player, String playerSpeech) {
    String masterName = plugin.getMasterName();
    String environmentStr =
        worldContextCollector.collectForPrompt(player, plugin.getMaxContextEntities());
    boolean isMaster = player.getName().equalsIgnoreCase(masterName);
    String identityStr = isMaster ? "主人" : "其他玩家";
    return """
        【本轮模式】
        PLAN

        【当前玩家】
        玩家名：%s
        身份：%s

        【玩家原话】
        %s

        【当前环境】
        %s

        %s
        """
        .formatted(
            player.getName(),
            identityStr,
            playerSpeech,
            environmentStr,
            runtimeContextCollector.collect(player));
  }

  private String buildJsonTurnSystemPrompt() {
    return plugin.getSystemPrompt()
        + """

        【CraftMaid JSON Turn Protocol v1】
        你每次必须只输出一个 JSON 对象，不要输出 Markdown、解释或代码块。
        JSON 格式：
        {
          "chat": "要对玩家说的话；如果需要执行 actions，则必须为空字符串",
          "actions": [
            {"type": "ACTION_TYPE", "name": "main", "target": "current"}
          ]
        }

        【可用 action】
        - FISHING_START(name)
        - FISHING_STOP(name)
        - HARVEST_START(name)
        - HARVEST_STOP(name)
        - CHUNK_KEEPER_START(name)
        - CHUNK_KEEPER_STOP(name)
        - JOB_STOP(target=current)
        - JOB_STATUS

        规则：
        1. 如果玩家只是闲聊、问候、提问，输出自然角色回复到 chat，actions=[]。
        2. 如果玩家要求你开始、停止、切换工作，输出 actions，chat=""。
        3. 如果 actions 非空，不要在 chat 中承诺已经完成；服务器会先执行 actions，再让你生成最终回复。
        4. 只允许使用列出的 action，不要编造 action，不要输出服务器命令。
        5. name 必须来自“可用工作配置”；如果玩家没有指定且只有一个可用配置，可以省略 name 让插件自动选择。
        6. 如果玩家说“别钓鱼了，快去收田”，输出 JOB_STOP + HARVEST_START。
        7. 如果不确定玩家是否在下命令，优先聊天，不执行 action。
        8. 如果本轮模式是 FINAL，actions 必须是 []，只能根据服务器动作结果生成最终 chat。
        9. chat 最多 80 个中文字符，必须是完整句子。
        """;
  }

  private String buildFinalPrompt(
      Player player, String playerSpeech, MaidActionExecutionResult actionResult) {
    return """
        【本轮模式】
        FINAL

        【玩家原话】
        %s

        【服务器已执行的动作结果】
        %s

        【执行后的状态】
        %s

        请根据动作结果和新状态，用女仆口吻简短回复玩家。
        这次 actions 必须为空数组，不要再请求任何 action。
        """
        .formatted(playerSpeech, actionResult.summary(), runtimeContextCollector.collect(player));
  }

  private String fallbackFinalReply(MaidActionExecutionResult actionResult) {
    String summary = actionResult.summary();
    if (summary.length() > 120) {
      summary = summary.substring(0, 120) + "...";
    }
    return "主人，动作结果是：" + summary;
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
