package com.github.quantumxiaol.craftmaid.config;

import java.util.List;
import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

public record CraftMaidConfig(
    LlmSettings llm,
    MaidSettings maid,
    ChatSettings chat,
    IntentSettings intent,
    PerceptionSettings perception,
    ConversationSettings conversation,
    JobSettings jobs,
    String systemPrompt) {
  private static final String DEFAULT_SYSTEM_PROMPT =
      "你是 Minecraft 里的一名可爱女仆，请始终用简短、乖巧的语气用{language}回复。你的主人是{master}，你的名字是{name}。不要打破第四面墙，表现出你真实地生活在 Minecraft 的世界中。";
  private static final List<String> DEFAULT_HOSTILE_TARGETS =
      List.of(
          "zombies",
          "skeletons",
          "strays",
          "husks",
          "drowned",
          "zombie_villagers",
          "spiders",
          "cave_spiders",
          "witches",
          "slimes",
          "magma_cubes",
          "blazes",
          "ghasts",
          "guardians",
          "elder_guardians",
          "silverfish",
          "endermites",
          "phantoms",
          "pillagers",
          "vindicators",
          "evokers",
          "ravagers",
          "vexes",
          "wither_skeletons",
          "withers",
          "hoglins",
          "zoglins",
          "bogged",
          "breeze",
          "creaking");
  private static final List<String> DEFAULT_FIGHTBACK_TARGETS =
      List.of("iron_golems", "piglins", "zombified_piglins", "endermen", "polar_bears");
  private static final List<String> DEFAULT_AVOID_TARGETS =
      List.of(
          "creepers",
          "bees",
          "wolves",
          "cats",
          "villagers",
          "wandering_traders",
          "snow_golems",
          "dolphins",
          "turtles",
          "foxes",
          "pandas",
          "llamas",
          "trader_llamas",
          "horses",
          "donkeys",
          "mules",
          "camels");

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
            Math.max(1, plugin.getConfig().getInt("llm.max_tokens", 320)),
            Math.max(1, plugin.getConfig().getInt("llm.timeout_seconds", 30)),
            Math.max(1, plugin.getConfig().getInt("llm.hard_timeout_seconds", 40)),
            Math.max(0, plugin.getConfig().getInt("llm.transient_retry_count", 1)),
            Math.max(0, plugin.getConfig().getInt("llm.transient_retry_delay_millis", 500)));

    MaidSettings maid =
        new MaidSettings(
            getConfigString(plugin, "maid.name", "露西"),
            getConfigString(plugin, "maid.master", "PlayerName"),
            getConfigString(plugin, "maid.language", "中文"),
            getConfigString(plugin, "maid.skin", "master"),
            new AccessSettings(
                plugin.getConfig().getBoolean("maid.access.admin_can_control", false),
                normalizeGuardTargetPolicy(
                    getConfigString(plugin, "maid.access.guard_target_policy", "master_only"))),
            new FollowSettings(
                clamp(plugin.getConfig().getDouble("maid.follow.speed", 1.75), 0.1, 3.0),
                Math.max(1, plugin.getConfig().getInt("maid.follow.update_ticks", 10)),
                clamp(plugin.getConfig().getDouble("maid.follow.stop_distance", 3.0), 0.0, 32.0),
                clamp(plugin.getConfig().getDouble("maid.follow.start_distance", 8.0), 0.0, 64.0),
                plugin.getConfig().getBoolean("maid.follow.teleport_enabled", true),
                clamp(
                    plugin.getConfig().getDouble("maid.follow.teleport_distance", 128.0),
                    4.0,
                    512.0),
                Math.max(0, plugin.getConfig().getInt("maid.follow.teleport_on_stuck_seconds", 0)),
                Math.max(0, plugin.getConfig().getInt("maid.follow.teleport_cooldown_seconds", 30)),
                Math.max(
                    1, plugin.getConfig().getInt("maid.follow.stuck_retry_before_teleport", 3)),
                clamp(
                    plugin.getConfig().getDouble("maid.follow.stuck_teleport_min_distance", 24.0),
                    1.0,
                    512.0),
                clamp(
                    plugin.getConfig().getDouble("maid.follow.straight_line_distance", 12.0),
                    0.0,
                    128.0),
                clamp(
                    plugin.getConfig().getDouble("maid.follow.destination_teleport_margin", -1.0),
                    -1.0,
                    512.0)),
            new CombatSettings(
                plugin.getConfig().getBoolean("maid.combat.enemy_drops", true),
                plugin.getConfig().getBoolean("maid.combat.enemy_exp", true),
                Math.max(0, plugin.getConfig().getInt("maid.combat.default_enemy_exp", 5)),
                getConfigStringList(plugin, "maid.combat.hostile_targets", DEFAULT_HOSTILE_TARGETS),
                getConfigStringList(
                    plugin, "maid.combat.fightback_targets", DEFAULT_FIGHTBACK_TARGETS),
                getConfigStringList(plugin, "maid.combat.avoid_targets", DEFAULT_AVOID_TARGETS),
                normalizeOwnerDamagePolicy(
                    getConfigString(plugin, "maid.combat.owner_damage_policy", "cancel")),
                plugin.getConfig().getBoolean("maid.combat.owner_attack_message", true),
                new SelfDefenseSettings(
                    plugin.getConfig().getBoolean("maid.combat.self_defense.enabled", true),
                    plugin.getConfig().getBoolean("maid.combat.self_defense.target_players", true),
                    plugin.getConfig().getBoolean("maid.combat.self_defense.target_master", false),
                    Math.max(
                        1,
                        plugin.getConfig().getInt("maid.combat.self_defense.duration_seconds", 20)),
                    clamp(
                        plugin
                            .getConfig()
                            .getDouble("maid.combat.self_defense.max_chase_distance", 24.0),
                        1.0,
                        128.0),
                    plugin
                        .getConfig()
                        .getBoolean("maid.combat.self_defense.forgive_when_attacker_far", true)),
                new GuardFightbackSettings(
                    plugin.getConfig().getBoolean("maid.combat.guard_fightback.enabled", true)),
                new SurvivabilitySettings(
                    plugin.getConfig().getBoolean("maid.combat.survivability.enabled", true),
                    clamp(
                        plugin
                            .getConfig()
                            .getDouble("maid.combat.survivability.sentinel_health", 40.0),
                        1.0,
                        1000.0),
                    clamp(
                        plugin
                            .getConfig()
                            .getDouble("maid.combat.survivability.sentinel_armor", 0.45),
                        0.0,
                        1.0),
                    clamp(
                        plugin
                            .getConfig()
                            .getDouble("maid.combat.survivability.sentinel_healrate_seconds", 2.0),
                        0.0,
                        120.0),
                    Math.max(
                        0,
                        plugin
                            .getConfig()
                            .getInt("maid.combat.survivability.sentinel_respawn_seconds", 10)),
                    plugin
                        .getConfig()
                        .getBoolean("maid.combat.survivability.sentinel_invincible", false),
                    plugin
                        .getConfig()
                        .getBoolean("maid.combat.survivability.sentinel_protected", true),
                    plugin
                        .getConfig()
                        .getBoolean("maid.combat.survivability.sentinel_fightback", false),
                    plugin.getConfig().getBoolean("maid.combat.survivability.potion_buffs", true),
                    Math.max(
                        0,
                        plugin
                            .getConfig()
                            .getInt("maid.combat.survivability.regeneration_amplifier", 0)),
                    Math.max(
                        0,
                        plugin
                            .getConfig()
                            .getInt("maid.combat.survivability.resistance_amplifier", 0)),
                    clamp(
                        plugin
                            .getConfig()
                            .getDouble("maid.combat.survivability.absorption_hearts", 4.0),
                        0.0,
                        40.0),
                    Math.max(
                        20,
                        plugin
                            .getConfig()
                            .getInt("maid.combat.survivability.refresh_ticks", 100)))));

    ChatSettings chat =
        new ChatSettings(
            Math.max(0, plugin.getConfig().getInt("chat.cooldown_seconds", 3)),
            Math.max(0, plugin.getConfig().getInt("chat.followup_seconds", 180)),
            Math.max(0, plugin.getConfig().getInt("chat.max_context_entities", 8)),
            plugin.getConfig().getString("chat.reply_prefix", "[{name}] "));

    IntentSettings intent =
        new IntentSettings(
            plugin.getConfig().getBoolean("intent.enabled", true),
            plugin.getConfig().getBoolean("intent.master_only", true),
            plugin.getConfig().getBoolean("intent.consume_on_match", true),
            plugin.getConfig().getBoolean("intent.allow_followup_window", true),
            plugin.getConfig().getBoolean("intent.llm_json", true),
            plugin.getConfig().getBoolean("intent.response_format_json_object", true),
            Math.max(160, plugin.getConfig().getInt("intent.plan_max_tokens", 1024)),
            clamp(plugin.getConfig().getDouble("intent.plan_temperature", 0.2), 0.0, 2.0),
            Math.max(120, plugin.getConfig().getInt("intent.final_max_tokens", 480)),
            clamp(plugin.getConfig().getDouble("intent.final_temperature", 0.6), 0.0, 2.0));

    PerceptionSettings perception =
        new PerceptionSettings(
            plugin.getConfig().getBoolean("perception.enabled", true),
            new EntityPerceptionSettings(
                plugin.getConfig().getBoolean("perception.entities.enabled", true),
                Math.max(1, plugin.getConfig().getInt("perception.entities.radius_xz", 12)),
                Math.max(1, plugin.getConfig().getInt("perception.entities.radius_y", 6)),
                Math.max(1, plugin.getConfig().getInt("perception.entities.max_entities", 24)),
                plugin.getConfig().getBoolean("perception.entities.include_passive", true),
                plugin.getConfig().getBoolean("perception.entities.include_neutral", true),
                plugin.getConfig().getBoolean("perception.entities.include_items", true)),
            new BlockPerceptionSettings(
                plugin.getConfig().getBoolean("perception.blocks.enabled", true),
                getConfigString(plugin, "perception.blocks.mode", "on_demand"),
                Math.max(1, plugin.getConfig().getInt("perception.blocks.radius_xz", 8)),
                Math.max(0, plugin.getConfig().getInt("perception.blocks.up", 3)),
                Math.max(0, plugin.getConfig().getInt("perception.blocks.down", 3)),
                Math.max(1, plugin.getConfig().getInt("perception.blocks.top_materials", 8)),
                Math.max(
                    1, plugin.getConfig().getInt("perception.blocks.max_blocks_scanned", 2500)),
                Math.max(0, plugin.getConfig().getInt("perception.blocks.cache_seconds", 10))),
            new TargetPerceptionSettings(
                plugin.getConfig().getBoolean("perception.target.enabled", true),
                Math.max(1, plugin.getConfig().getInt("perception.target.max_distance", 10))));

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
            new JobNavigationSettings(
                clamp(plugin.getConfig().getDouble("jobs.navigation.speed", 1.5), 0.1, 3.0),
                Math.max(1, plugin.getConfig().getInt("jobs.navigation.update_ticks", 20)),
                clamp(
                    plugin.getConfig().getDouble("jobs.navigation.arrival_distance", 3.0),
                    0.5,
                    16.0),
                Math.max(
                    5, plugin.getConfig().getInt("jobs.navigation.arrival_timeout_seconds", 180)),
                Math.max(20, plugin.getConfig().getInt("jobs.navigation.retry_ticks", 100)),
                clamp(
                    plugin.getConfig().getDouble("jobs.navigation.straight_line_distance", 12.0),
                    0.0,
                    128.0)),
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

    return new CraftMaidConfig(
        llm, maid, chat, intent, perception, conversation, jobs, systemPrompt);
  }

  private static String getConfigString(JavaPlugin plugin, String path, String defaultValue) {
    String value = plugin.getConfig().getString(path, defaultValue);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value.trim();
  }

  private static List<String> getConfigStringList(
      JavaPlugin plugin, String path, List<String> defaultValue) {
    List<String> values = plugin.getConfig().getStringList(path);
    if (values == null || values.isEmpty()) {
      return defaultValue;
    }
    return values.stream().filter(value -> value != null && !value.isBlank()).toList();
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

  private static String normalizeOwnerDamagePolicy(String value) {
    String normalized =
        value == null ? "cancel" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "allow_no_retaliate", "allow_and_retaliate" -> normalized;
      default -> "cancel";
    };
  }

  private static String normalizeGuardTargetPolicy(String value) {
    String normalized =
        value == null ? "master_only" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return "current_player".equals(normalized) ? normalized : "master_only";
  }

  public record LlmSettings(
      String baseUrl,
      String apiKey,
      String modelName,
      double temperature,
      int maxTokens,
      int timeoutSeconds,
      int hardTimeoutSeconds,
      int transientRetryCount,
      int transientRetryDelayMillis) {}

  public record MaidSettings(
      String name,
      String master,
      String language,
      String skin,
      AccessSettings access,
      FollowSettings follow,
      CombatSettings combat) {}

  public record AccessSettings(boolean adminCanControl, String guardTargetPolicy) {}

  public record FollowSettings(
      double speed,
      int updateTicks,
      double stopDistance,
      double startDistance,
      boolean teleportEnabled,
      double teleportDistance,
      int teleportOnStuckSeconds,
      int teleportCooldownSeconds,
      int stuckRetryBeforeTeleport,
      double stuckTeleportMinDistance,
      double straightLineDistance,
      double destinationTeleportMargin) {}

  public record CombatSettings(
      boolean enemyDrops,
      boolean enemyExp,
      int defaultEnemyExp,
      List<String> hostileTargets,
      List<String> fightbackTargets,
      List<String> avoidTargets,
      String ownerDamagePolicy,
      boolean ownerAttackMessage,
      SelfDefenseSettings selfDefense,
      GuardFightbackSettings guardFightback,
      SurvivabilitySettings survivability) {}

  public record SelfDefenseSettings(
      boolean enabled,
      boolean targetPlayers,
      boolean targetMaster,
      int durationSeconds,
      double maxChaseDistance,
      boolean forgiveWhenAttackerFar) {}

  public record GuardFightbackSettings(boolean enabled) {}

  public record SurvivabilitySettings(
      boolean enabled,
      double sentinelHealth,
      double sentinelArmor,
      double sentinelHealrateSeconds,
      int sentinelRespawnSeconds,
      boolean sentinelInvincible,
      boolean sentinelProtected,
      boolean sentinelFightback,
      boolean potionBuffs,
      int regenerationAmplifier,
      int resistanceAmplifier,
      double absorptionHearts,
      int refreshTicks) {}

  public record ChatSettings(
      int cooldownSeconds, int followupSeconds, int maxContextEntities, String replyPrefix) {}

  public record IntentSettings(
      boolean enabled,
      boolean masterOnly,
      boolean consumeOnMatch,
      boolean allowFollowupWindow,
      boolean llmJson,
      boolean responseFormatJsonObject,
      int planMaxTokens,
      double planTemperature,
      int finalMaxTokens,
      double finalTemperature) {}

  public record PerceptionSettings(
      boolean enabled,
      EntityPerceptionSettings entities,
      BlockPerceptionSettings blocks,
      TargetPerceptionSettings target) {}

  public record EntityPerceptionSettings(
      boolean enabled,
      int radiusXz,
      int radiusY,
      int maxEntities,
      boolean includePassive,
      boolean includeNeutral,
      boolean includeItems) {}

  public record BlockPerceptionSettings(
      boolean enabled,
      String mode,
      int radiusXz,
      int up,
      int down,
      int topMaterials,
      int maxBlocksScanned,
      int cacheSeconds) {}

  public record TargetPerceptionSettings(boolean enabled, int maxDistance) {}

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
      JobNavigationSettings navigation,
      FishingSettings fishing,
      ChunkKeeperSettings chunkKeeper,
      HarvestSettings harvest) {}

  public record JobNavigationSettings(
      double speed,
      int updateTicks,
      double arrivalDistance,
      int arrivalTimeoutSeconds,
      int retryTicks,
      double straightLineDistance) {}

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
