package com.github.quantumxiaol.craftmaid.config;

import org.bukkit.plugin.java.JavaPlugin;

public record CraftMaidConfig(
    LlmSettings llm,
    MaidSettings maid,
    ChatSettings chat,
    ConversationSettings conversation,
    JobSettings jobs,
    String systemPrompt) {
  private static final String DEFAULT_SYSTEM_PROMPT =
      "你是 Minecraft 里的一名可爱女仆，请始终用简短、乖巧的语气用{language}回复。你的主人是{master}，你的名字是{name}。不要打破第四面墙，表现出你真实地生活在 Minecraft 的世界中。";

  public static CraftMaidConfig load(JavaPlugin plugin) {
    plugin.reloadConfig();

    String apiKey = getConfigString(plugin, "llm.api_key", "");
    if ("your-api-key-here".equals(apiKey)) {
      apiKey = "";
    }

    LlmSettings llm =
        new LlmSettings(
            getConfigString(plugin, "llm.base_url", "https://api.openai.com/v1/chat/completions"),
            apiKey,
            getConfigString(plugin, "llm.model_name", "gpt-3.5-turbo"),
            clamp(plugin.getConfig().getDouble("llm.temperature", 0.7), 0.0, 2.0),
            Math.max(1, plugin.getConfig().getInt("llm.max_tokens", 180)),
            Math.max(1, plugin.getConfig().getInt("llm.timeout_seconds", 30)));

    MaidSettings maid =
        new MaidSettings(
            getConfigString(plugin, "maid.name", "露西"),
            getConfigString(plugin, "maid.master", "PlayerName"),
            getConfigString(plugin, "maid.language", "中文"),
            getConfigString(plugin, "maid.skin", "master"),
            plugin.getConfig().getBoolean("maid.combat.enemy_drops", true),
            plugin.getConfig().getBoolean("maid.combat.enemy_exp", true),
            Math.max(0, plugin.getConfig().getInt("maid.combat.default_enemy_exp", 5)),
            clamp(plugin.getConfig().getDouble("maid.follow.speed", 1.35), 0.1, 3.0));

    ChatSettings chat =
        new ChatSettings(
            Math.max(0, plugin.getConfig().getInt("chat.cooldown_seconds", 3)),
            Math.max(0, plugin.getConfig().getInt("chat.followup_seconds", 180)),
            Math.max(0, plugin.getConfig().getInt("chat.max_context_entities", 8)),
            plugin.getConfig().getString("chat.reply_prefix", "[{name}] "));

    ConversationSettings conversation =
        new ConversationSettings(
            plugin.getConfig().getBoolean("conversation.enabled", true),
            Math.max(1, plugin.getConfig().getInt("conversation.max_messages", 100)),
            Math.max(80, plugin.getConfig().getInt("conversation.max_message_chars", 1200)),
            Math.max(400, plugin.getConfig().getInt("conversation.max_memory_chars", 4000)),
            Math.max(200, plugin.getConfig().getInt("conversation.summary.max_tokens", 900)),
            clamp(plugin.getConfig().getDouble("conversation.summary.temperature", 0.2), 0.0, 2.0),
            plugin.getConfig().getBoolean("conversation.persist.enabled", false),
            getConfigString(plugin, "conversation.persist.file", "conversations.json"));

    JobSettings jobs =
        new JobSettings(
            new FishingSettings(
                Math.max(20, plugin.getConfig().getInt("jobs.fishing.min_wait_ticks", 100)),
                Math.max(20, plugin.getConfig().getInt("jobs.fishing.max_wait_ticks", 240)),
                Math.max(0.0, plugin.getConfig().getDouble("jobs.fishing.fish_weight", 72.0)),
                Math.max(0.0, plugin.getConfig().getDouble("jobs.fishing.junk_weight", 23.0)),
                Math.max(0.0, plugin.getConfig().getDouble("jobs.fishing.treasure_weight", 5.0)),
                plugin.getConfig().getBoolean("jobs.fishing.treasure_enabled", true),
                plugin.getConfig().getBoolean("jobs.fishing.denizen_animation", false)),
            new ChunkKeeperSettings(
                Math.max(0, plugin.getConfig().getInt("jobs.chunk_keeper.radius_chunks", 0)),
                plugin.getConfig().getBoolean("jobs.chunk_keeper.guard_with_sentinel", false)),
            new HarvestSettings(
                Math.max(1, plugin.getConfig().getInt("jobs.harvest.max_region_volume", 4096)),
                Math.max(1, plugin.getConfig().getInt("jobs.harvest.max_blocks_per_tick", 4)),
                Math.max(1, plugin.getConfig().getInt("jobs.harvest.max_blocks_per_run", 128))));

    String rawSystemPrompt =
        plugin.getConfig().getString("maid.system_prompt", DEFAULT_SYSTEM_PROMPT);
    String systemPrompt = renderSystemPrompt(rawSystemPrompt, maid);

    return new CraftMaidConfig(llm, maid, chat, conversation, jobs, systemPrompt);
  }

  private static String getConfigString(JavaPlugin plugin, String path, String defaultValue) {
    String value = plugin.getConfig().getString(path, defaultValue);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value.trim();
  }

  private static String renderSystemPrompt(String rawSystemPrompt, MaidSettings maid) {
    String prompt =
        rawSystemPrompt == null || rawSystemPrompt.isBlank()
            ? DEFAULT_SYSTEM_PROMPT
            : rawSystemPrompt;
    return prompt
        .replace("{language}", maid.language())
        .replace("{master}", maid.master())
        .replace("{name}", maid.name());
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  public record LlmSettings(
      String baseUrl,
      String apiKey,
      String modelName,
      double temperature,
      int maxTokens,
      int timeoutSeconds) {}

  public record MaidSettings(
      String name,
      String master,
      String language,
      String skin,
      boolean enemyDrops,
      boolean enemyExp,
      int defaultEnemyExp,
      double followSpeed) {}

  public record ChatSettings(
      int cooldownSeconds, int followupSeconds, int maxContextEntities, String replyPrefix) {}

  public record ConversationSettings(
      boolean enabled,
      int maxMessages,
      int maxMessageChars,
      int maxMemoryChars,
      int summaryMaxTokens,
      double summaryTemperature,
      boolean persistEnabled,
      String persistFile) {}

  public record JobSettings(
      FishingSettings fishing, ChunkKeeperSettings chunkKeeper, HarvestSettings harvest) {}

  public record FishingSettings(
      int minWaitTicks,
      int maxWaitTicks,
      double fishWeight,
      double junkWeight,
      double treasureWeight,
      boolean treasureEnabled,
      boolean denizenAnimation) {}

  public record ChunkKeeperSettings(int radiusChunks, boolean guardWithSentinel) {}

  public record HarvestSettings(int maxRegionVolume, int maxBlocksPerTick, int maxBlocksPerRun) {}
}
