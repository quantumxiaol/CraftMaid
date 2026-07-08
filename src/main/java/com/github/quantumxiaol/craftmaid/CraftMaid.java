package com.github.quantumxiaol.craftmaid;

import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.combat.MaidCombatBuffService;
import com.github.quantumxiaol.craftmaid.combat.MaidCombatFightbackListener;
import com.github.quantumxiaol.craftmaid.combat.MaidCombatPolicy;
import com.github.quantumxiaol.craftmaid.combat.MaidDamagePolicyListener;
import com.github.quantumxiaol.craftmaid.combat.MaidLootListener;
import com.github.quantumxiaol.craftmaid.command.CraftMaidCommand;
import com.github.quantumxiaol.craftmaid.command.FollowCommand;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService;
import com.github.quantumxiaol.craftmaid.job.MaidJobEventBuffer;
import com.github.quantumxiaol.craftmaid.job.MaidJobService;
import com.github.quantumxiaol.craftmaid.llm.LlmClient;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcServices;
import com.github.quantumxiaol.craftmaid.perception.MaidPerceptionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftMaid extends JavaPlugin {
  private CraftMaidConfig config;
  private LlmClient llmClient;
  private ChatListener chatListener;
  private MaidAnchorService anchorService;
  private ConversationHistory conversationHistory;
  private MaidInventoryService maidInventoryService;
  private MaidJobEventBuffer jobEventBuffer;
  private MaidJobService jobService;
  private MaidNpcService maidNpcService;
  private MaidMenuService maidMenuService;
  private MaidCombatPolicy combatPolicy;
  private MaidCombatBuffService combatBuffService;
  private MaidPerceptionService perceptionService;

  @Override
  public void onEnable() {
    getLogger().info("🎀 CraftMaid 正在唤醒女仆...");

    saveDefaultConfig();
    anchorService = new MaidAnchorService(this);
    anchorService.load();
    conversationHistory = new ConversationHistory(this);
    maidInventoryService = new MaidInventoryService(this);
    jobEventBuffer = new MaidJobEventBuffer();
    jobService = new MaidJobService(this);
    maidNpcService = MaidNpcServices.create(this);
    maidMenuService = new MaidMenuService(this);
    combatBuffService = new MaidCombatBuffService(this);
    perceptionService = new MaidPerceptionService(this);
    if (!maidNpcService.isAvailable()) {
      getLogger().warning("找不到 Citizens 插件或 NPC 服务不可用！(实体功能受限，但不影响聊天测试)");
    }
    loadConfiguration();

    chatListener = new ChatListener(this, llmClient);
    getServer().getPluginManager().registerEvents(chatListener, this);
    getServer().getPluginManager().registerEvents(maidMenuService, this);
    getServer().getPluginManager().registerEvents(new MaidLootListener(this), this);
    getServer().getPluginManager().registerEvents(new MaidDamagePolicyListener(this), this);
    getServer().getPluginManager().registerEvents(new MaidCombatFightbackListener(this), this);
    maidNpcService.registerInteractionListener(maidMenuService);

    PluginCommand craftMaidCommand = getCommand("craftmaid");
    if (craftMaidCommand == null) {
      getLogger().severe("plugin.yml 中缺少 craftmaid 命令，命令功能不可用。");
    } else {
      CraftMaidCommand commandExecutor = new CraftMaidCommand(this);
      craftMaidCommand.setExecutor(commandExecutor);
      craftMaidCommand.setTabCompleter(commandExecutor);
    }

    PluginCommand followCommand = getCommand("follow");
    if (followCommand == null) {
      getLogger().severe("plugin.yml 中缺少 follow 命令，跟随快捷命令不可用。");
    } else {
      FollowCommand commandExecutor = new FollowCommand(this);
      followCommand.setExecutor(commandExecutor);
      followCommand.setTabCompleter(commandExecutor);
    }

    getLogger().info("🎀 CraftMaid 启动完毕！在游戏里喊“" + getMaidName() + "”试试看。");
  }

  @Override
  public void onDisable() {
    if (maidNpcService != null) {
      maidNpcService.stopFollowing();
    }
    if (combatBuffService != null) {
      combatBuffService.stop();
    }
    if (jobService != null) {
      jobService.shutdown();
    }
    if (conversationHistory != null) {
      conversationHistory.save();
    }
    getLogger().info("🎀 CraftMaid 正在休息...");
  }

  public void loadConfiguration() {
    this.config = CraftMaidConfig.load(this);
    this.combatPolicy = MaidCombatPolicy.from(config.maid().combat());
    if (anchorService != null) {
      anchorService.load();
    }
    if (combatBuffService != null) {
      combatBuffService.restartIfGuarding();
    }

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

  public String getMaidSkin() {
    return config.maid().skin();
  }

  public boolean isMaidEnemyDropsEnabled() {
    return config.maid().combat().enemyDrops();
  }

  public boolean isMaidEnemyExpEnabled() {
    return config.maid().combat().enemyExp();
  }

  public int getMaidDefaultEnemyExp() {
    return config.maid().combat().defaultEnemyExp();
  }

  public double getMaidFollowSpeed() {
    return config.maid().follow().speed();
  }

  public CraftMaidConfig.FollowSettings getMaidFollowSettings() {
    return config.maid().follow();
  }

  public CraftMaidConfig.CombatSettings getMaidCombatSettings() {
    return config.maid().combat();
  }

  public CraftMaidConfig.SurvivabilitySettings getMaidSurvivabilitySettings() {
    return config.maid().combat().survivability();
  }

  public MaidCombatPolicy getMaidCombatPolicy() {
    return combatPolicy;
  }

  public MaidCombatBuffService getMaidCombatBuffService() {
    return combatBuffService;
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

  public CraftMaidConfig.IntentSettings getIntentSettings() {
    return config.intent();
  }

  public CraftMaidConfig.PerceptionSettings getPerceptionSettings() {
    return config.perception();
  }

  public MaidPerceptionService getPerceptionService() {
    return perceptionService;
  }

  public int getConversationSummaryMaxTokens() {
    return config.conversation().summaryMaxTokens();
  }

  public double getConversationSummaryTemperature() {
    return config.conversation().summaryTemperature();
  }

  public CraftMaidConfig.JobSettings getJobSettings() {
    return config.jobs();
  }

  public CraftMaidConfig.JobNavigationSettings getJobNavigationSettings() {
    return config.jobs().navigation();
  }

  public CraftMaidConfig.FishingSettings getFishingSettings() {
    return config.jobs().fishing();
  }

  public CraftMaidConfig.ChunkKeeperSettings getChunkKeeperSettings() {
    return config.jobs().chunkKeeper();
  }

  public CraftMaidConfig.HarvestSettings getHarvestSettings() {
    return config.jobs().harvest();
  }

  public ConversationHistory getConversationHistory() {
    return conversationHistory;
  }

  public MaidAnchorService getAnchorService() {
    return anchorService;
  }

  public MaidInventoryService getMaidInventoryService() {
    return maidInventoryService;
  }

  public MaidJobEventBuffer getJobEventBuffer() {
    return jobEventBuffer;
  }

  public MaidJobService getJobService() {
    return jobService;
  }

  public MaidNpcService getMaidNpcService() {
    return maidNpcService;
  }
}
