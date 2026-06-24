package com.github.quantumxiaol.craftmaid;

import com.github.quantumxiaol.craftmaid.command.CraftMaidCommand;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.llm.LlmClient;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftMaid extends JavaPlugin {
  private CraftMaidConfig config;
  private LlmClient llmClient;
  private ChatListener chatListener;
  private ConversationHistory conversationHistory;
  private MaidNpcService maidNpcService;

  @Override
  public void onEnable() {
    getLogger().info("🎀 CraftMaid 正在唤醒女仆...");

    if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
      getLogger().warning("找不到 Citizens 插件！(实体功能受限，但不影响聊天测试)");
    }

    saveDefaultConfig();
    conversationHistory = new ConversationHistory(this);
    maidNpcService = new MaidNpcService(this);
    loadConfiguration();

    chatListener = new ChatListener(this, llmClient);
    getServer().getPluginManager().registerEvents(chatListener, this);

    PluginCommand craftMaidCommand = getCommand("craftmaid");
    if (craftMaidCommand == null) {
      getLogger().severe("plugin.yml 中缺少 craftmaid 命令，命令功能不可用。");
    } else {
      CraftMaidCommand commandExecutor = new CraftMaidCommand(this);
      craftMaidCommand.setExecutor(commandExecutor);
      craftMaidCommand.setTabCompleter(commandExecutor);
    }

    getLogger().info("🎀 CraftMaid 启动完毕！在游戏里喊“" + getMaidName() + "”试试看。");
  }

  @Override
  public void onDisable() {
    if (conversationHistory != null) {
      conversationHistory.save();
    }
    getLogger().info("🎀 CraftMaid 正在休息...");
  }

  public void loadConfiguration() {
    this.config = CraftMaidConfig.load(this);

    CraftMaidConfig.LlmSettings llm = config.llm();
    this.llmClient =
        new LlmClient(
            llm.baseUrl(),
            llm.apiKey(),
            llm.modelName(),
            llm.temperature(),
            llm.maxTokens(),
            llm.timeoutSeconds());
    if (llm.baseUrl().contains("api.openai.com") && llm.apiKey().isBlank()) {
      getLogger().warning("当前 LLM 地址是 OpenAI API，但未配置 llm.api_key。");
    }

    CraftMaidConfig.ConversationSettings conversation = config.conversation();
    conversationHistory.configure(
        conversation.enabled(),
        conversation.maxMessages(),
        conversation.maxMessageChars(),
        conversation.maxMemoryChars(),
        conversation.persistEnabled(),
        conversation.persistFile());

    if (chatListener != null) {
      chatListener.updateClient(llmClient);
    }
  }

  public void reloadPlugin() {
    loadConfiguration();
  }

  public String getMaidName() {
    return config.maid().name();
  }

  public String getMasterName() {
    return config.maid().master();
  }

  public String getSystemPrompt() {
    return config.systemPrompt();
  }

  public int getChatCooldownSeconds() {
    return config.chat().cooldownSeconds();
  }

  public int getChatFollowupSeconds() {
    return config.chat().followupSeconds();
  }

  public int getMaxContextEntities() {
    return config.chat().maxContextEntities();
  }

  public String getReplyPrefix() {
    return config.chat().replyPrefix();
  }

  public int getConversationSummaryMaxTokens() {
    return config.conversation().summaryMaxTokens();
  }

  public double getConversationSummaryTemperature() {
    return config.conversation().summaryTemperature();
  }

  public ConversationHistory getConversationHistory() {
    return conversationHistory;
  }

  public MaidNpcService getMaidNpcService() {
    return maidNpcService;
  }
}
