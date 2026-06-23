package com.github.quantumxiaol.craftmaid;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatListener implements Listener {
    private final CraftMaid plugin;
    private final Map<UUID, Long> nextAllowedReplyAt = new ConcurrentHashMap<>();
    private final Set<UUID> respondingPlayers = ConcurrentHashMap.newKeySet();
    private volatile LlmClient llmClient;

    public ChatListener(CraftMaid plugin, LlmClient llmClient) {
        this.plugin = plugin;
        this.llmClient = llmClient;
    }

    public void updateClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        String maidName = plugin.getMaidName();

        if (!containsMaidName(rawMessage, maidName)) {
            return;
        }

        LlmClient client = this.llmClient;
        if (client == null) {
            plugin.getLogger().warning("LLM 客户端未初始化，已跳过本次回复。");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleMaidMention(player, rawMessage, maidName, client));
    }

    private void handleMaidMention(Player player, String rawMessage, String maidName, LlmClient client) {
        if (!player.isOnline()) {
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

        World world = player.getWorld();
        boolean isRaining = world.hasStorm();
        String timeStr = (world.getTime() < 12000) ? "白天" : "夜晚";

        List<Entity> nearbyEntities = player.getNearbyEntities(10, 10, 10);
        String environmentEntities = nearbyEntities.stream()
                .filter(e -> e instanceof Monster || e instanceof Player)
                .map(this::describeEntity)
                .distinct()
                .limit(plugin.getMaxContextEntities())
                .collect(Collectors.joining(", "));
        
        String environmentStr = String.format("现在是%s，%s。", timeStr, isRaining ? "正在下雨" : "天气晴朗");
        if (!environmentEntities.isEmpty()) {
            environmentStr += " 你的周围有这些生物或玩家: " + environmentEntities + "。";
        }

        boolean isMaster = player.getName().equalsIgnoreCase(masterName);
        String identityStr = isMaster ? "主人" : "其他玩家";
        
        String systemPrompt = plugin.getSystemPrompt();
        String playerSpeech = stripMaidName(rawMessage, maidName);
        if (playerSpeech.isBlank()) {
            playerSpeech = "正在呼唤你，请自然回应。";
        }

        String userPrompt = String.format(
            "当前环境：%s 跟我说话的人是 %s (%s) 对我说：“%s”",
            environmentStr, playerName, identityStr, playerSpeech
        );
        List<ConversationMessage> conversationMessages = plugin.getConversationHistory()
                .buildPromptMessages(playerId, userPrompt);

        client.askAiAsync(systemPrompt, conversationMessages).whenComplete((reply, ex) -> {
            respondingPlayers.remove(playerId);
            if (!plugin.isEnabled()) {
                return;
            }

            if (ex != null) {
                plugin.getLogger().warning("请求 AI 失败: " + rootMessage(ex));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(Component.text(maidName + " 暂时没有回应，请稍后再试。", NamedTextColor.RED));
                    }
                });
                return;
            }

            String cleanReply = reply == null ? "" : reply.trim();
            if (cleanReply.isBlank()) {
                return;
            }

            plugin.getConversationHistory().appendExchange(playerId, playerName, userPrompt, cleanReply);
            triggerMemoryCompression(playerId, client);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                String prefix = plugin.getReplyPrefix().replace("{name}", maidName);
                Component replyComponent = Component.text(prefix + cleanReply, NamedTextColor.LIGHT_PURPLE);
                Bukkit.broadcast(replyComponent);
            });
        });
    }

    private void triggerMemoryCompression(UUID playerId, LlmClient client) {
        ConversationHistory.CompressionRequest compressionRequest = plugin.getConversationHistory().prepareCompression(playerId);
        if (compressionRequest == null) {
            return;
        }

        client.summarizeMemoryAsync(
                compressionRequest,
                plugin.getConversationSummaryMaxTokens(),
                plugin.getConversationSummaryTemperature()
        ).whenComplete((memorySummary, ex) -> {
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

    private String describeEntity(Entity entity) {
        if (entity instanceof Player nearbyPlayer) {
            return "玩家 " + nearbyPlayer.getName();
        }
        return entity.getName();
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}
