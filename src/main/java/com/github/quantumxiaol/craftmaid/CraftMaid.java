package com.github.quantumxiaol.craftmaid;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftMaid extends JavaPlugin {
    private static final String DEFAULT_SYSTEM_PROMPT = "你是 Minecraft 里的一名可爱女仆，请始终用简短、乖巧的语气用{language}回复。你的主人是{master}，你的名字是{name}。不要打破第四面墙，表现出你真实地生活在 Minecraft 的世界中。";

    private LlmClient llmClient;
    private ChatListener chatListener;
    private ConversationHistory conversationHistory;
    private String maidName;
    private String masterName;
    private String systemPrompt;
    private int chatCooldownSeconds;
    private int chatFollowupSeconds;
    private int maxContextEntities;
    private int conversationSummaryMaxTokens;
    private double conversationSummaryTemperature;
    private String replyPrefix;

    @Override
    public void onEnable() {
        getLogger().info("🎀 CraftMaid 正在唤醒女仆...");

        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().warning("找不到 Citizens 插件！(实体功能受限，但不影响聊天测试)");
        }

        saveDefaultConfig();
        conversationHistory = new ConversationHistory(this);
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

        getLogger().info("🎀 CraftMaid 启动完毕！在游戏里喊“" + maidName + "”试试看。");
    }

    @Override
    public void onDisable() {
        if (conversationHistory != null) {
            conversationHistory.save();
        }
        getLogger().info("🎀 CraftMaid 正在休息...");
    }

    public void loadConfiguration() {
        reloadConfig();
        
        String apiEndpoint = getConfigString("llm.base_url", "https://api.openai.com/v1/chat/completions");
        String apiKey = getConfigString("llm.api_key", "");
        if ("your-api-key-here".equals(apiKey)) {
            apiKey = "";
        }
        String modelName = getConfigString("llm.model_name", "gpt-3.5-turbo");
        int timeoutSeconds = Math.max(1, getConfig().getInt("llm.timeout_seconds", 30));
        int maxTokens = Math.max(1, getConfig().getInt("llm.max_tokens", 180));
        double temperature = clamp(getConfig().getDouble("llm.temperature", 0.7), 0.0, 2.0);
        
        this.maidName = getConfigString("maid.name", "露西");
        this.masterName = getConfigString("maid.master", "PlayerName");
        String language = getConfigString("maid.language", "中文");
        this.chatCooldownSeconds = Math.max(0, getConfig().getInt("chat.cooldown_seconds", 3));
        this.chatFollowupSeconds = Math.max(0, getConfig().getInt("chat.followup_seconds", 180));
        this.maxContextEntities = Math.max(0, getConfig().getInt("chat.max_context_entities", 8));
        this.replyPrefix = getConfig().getString("chat.reply_prefix", "[{name}] ");
        boolean conversationEnabled = getConfig().getBoolean("conversation.enabled", true);
        int conversationMaxMessages = Math.max(1, getConfig().getInt("conversation.max_messages", 100));
        int conversationMaxMessageChars = Math.max(80, getConfig().getInt("conversation.max_message_chars", 1200));
        int conversationMaxMemoryChars = Math.max(400, getConfig().getInt("conversation.max_memory_chars", 4000));
        this.conversationSummaryMaxTokens = Math.max(200, getConfig().getInt("conversation.summary.max_tokens", 900));
        this.conversationSummaryTemperature = clamp(getConfig().getDouble("conversation.summary.temperature", 0.2), 0.0, 2.0);
        boolean conversationPersistEnabled = getConfig().getBoolean("conversation.persist.enabled", false);
        String conversationPersistFile = getConfigString("conversation.persist.file", "conversations.json");
        
        String rawSystemPrompt = getConfig().getString("maid.system_prompt", DEFAULT_SYSTEM_PROMPT);
        this.systemPrompt = renderSystemPrompt(rawSystemPrompt, language);

        this.llmClient = new LlmClient(apiEndpoint, apiKey, modelName, temperature, maxTokens, timeoutSeconds);
        if (apiEndpoint.contains("api.openai.com") && apiKey.isBlank()) {
            getLogger().warning("当前 LLM 地址是 OpenAI API，但未配置 llm.api_key。");
        }
        conversationHistory.configure(
                conversationEnabled,
                conversationMaxMessages,
                conversationMaxMessageChars,
                conversationMaxMemoryChars,
                conversationPersistEnabled,
                conversationPersistFile
        );
        
        if (chatListener != null) {
            chatListener.updateClient(llmClient);
        }
    }

    public void reloadPlugin() {
        loadConfiguration();
    }

    public String getMaidName() {
        return maidName;
    }

    public String getMasterName() {
        return masterName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public int getChatCooldownSeconds() {
        return chatCooldownSeconds;
    }

    public int getChatFollowupSeconds() {
        return chatFollowupSeconds;
    }

    public int getMaxContextEntities() {
        return maxContextEntities;
    }

    public String getReplyPrefix() {
        return replyPrefix;
    }

    public int getConversationSummaryMaxTokens() {
        return conversationSummaryMaxTokens;
    }

    public double getConversationSummaryTemperature() {
        return conversationSummaryTemperature;
    }

    public ConversationHistory getConversationHistory() {
        return conversationHistory;
    }

    private String getConfigString(String path, String defaultValue) {
        String value = getConfig().getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String renderSystemPrompt(String rawSystemPrompt, String language) {
        String prompt = rawSystemPrompt == null || rawSystemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : rawSystemPrompt;
        return prompt
                .replace("{language}", language)
                .replace("{master}", this.masterName)
                .replace("{name}", this.maidName);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
