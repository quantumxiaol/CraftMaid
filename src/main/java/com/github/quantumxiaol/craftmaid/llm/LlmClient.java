package com.github.quantumxiaol.craftmaid.llm;

import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LlmClient {
  private static final String MEMORY_COMPRESSION_SYSTEM_PROMPT =
      """
            你是 CraftMaid 的长期记忆压缩器。你的任务是把已有长期 Memory 和旧对话合并成结构化中文摘要，供 Minecraft 女仆角色继续多轮对话使用。
            只输出摘要，不要解释过程，不要复述原文。
            固定使用以下结构：
            【玩家偏好】
            - ...
            【已承诺事项】
            - ...
            【世界状态】
            - ...
            【重要关系】
            - ...
            【最近目标】
            - ...
            规则：
            - 只保留未来对话有用的信息；删除寒暄、重复环境、一次性闲聊和过期细节。
            - 合并已有 Memory 与旧对话；如果有冲突，优先保留较新的旧对话。
            - 不要编造没有出现过的信息；没有内容的栏目写“暂无”。
            - 摘要要简洁，但保留玩家偏好、承诺、关系和目标中的具体名词。
            """;

  private final HttpClient httpClient;
  private final String apiUrl;
  private final String apiKey;
  private final String modelName;
  private final double temperature;
  private final int maxTokens;
  private final Duration requestTimeout;

  public LlmClient(
      String apiUrl,
      String apiKey,
      String modelName,
      double temperature,
      int maxTokens,
      int timeoutSeconds) {
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    this.apiUrl = normalizeApiUrl(apiUrl);
    this.apiKey = apiKey;
    this.modelName = modelName;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
  }

  public CompletableFuture<String> askAiAsync(String systemPrompt, String userPrompt) {
    return askAiAsync(systemPrompt, List.of(ConversationMessage.user(userPrompt)));
  }

  public CompletableFuture<String> askAiAsync(
      String systemPrompt, List<ConversationMessage> conversationMessages) {
    return askAiAsync(systemPrompt, conversationMessages, this.maxTokens, this.temperature);
  }

  public CompletableFuture<String> summarizeMemoryAsync(
      ConversationHistory.CompressionRequest request, int maxTokens, double temperature) {
    if (request == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("压缩请求不能为空"));
    }
    int safeMaxTokens = Math.max(200, maxTokens);
    double safeTemperature = Math.max(0.0, Math.min(2.0, temperature));
    return askAiAsync(
        MEMORY_COMPRESSION_SYSTEM_PROMPT,
        List.of(ConversationMessage.user(request.toPrompt())),
        safeMaxTokens,
        safeTemperature);
  }

  private CompletableFuture<String> askAiAsync(
      String systemPrompt,
      List<ConversationMessage> conversationMessages,
      int maxTokens,
      double temperature) {
    URI uri;
    try {
      uri = URI.create(this.apiUrl);
    } catch (IllegalArgumentException ex) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("LLM API 地址无效: " + this.apiUrl, ex));
    }
    if (!uri.isAbsolute()) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("LLM API 地址必须是完整 URL: " + this.apiUrl));
    }

    JsonObject payload = new JsonObject();
    payload.addProperty("model", this.modelName);
    payload.addProperty("temperature", temperature);
    payload.addProperty("max_tokens", maxTokens);

    JsonArray messages = new JsonArray();

    if (systemPrompt != null && !systemPrompt.isEmpty()) {
      JsonObject sysMessage = new JsonObject();
      sysMessage.addProperty("role", "system");
      sysMessage.addProperty("content", systemPrompt);
      messages.add(sysMessage);
    }

    List<ConversationMessage> safeConversationMessages =
        conversationMessages == null ? List.of() : conversationMessages;
    for (ConversationMessage conversationMessage : safeConversationMessages) {
      if (conversationMessage == null || !conversationMessage.isValid()) {
        continue;
      }
      JsonObject message = new JsonObject();
      message.addProperty("role", conversationMessage.role());
      message.addProperty("content", conversationMessage.content());
      messages.add(message);
    }
    payload.add("messages", messages);

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(this.requestTimeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "CraftMaid")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));

    if (this.apiKey != null && !this.apiKey.isEmpty()) {
      requestBuilder.header("Authorization", "Bearer " + this.apiKey);
    }

    HttpRequest request = requestBuilder.build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(
            response -> {
              try {
                return parseResponse(response);
              } catch (JsonParseException | IllegalStateException | NullPointerException ex) {
                throw new RuntimeException("LLM 返回格式无法解析: " + summarize(response.body()), ex);
              }
            });
  }

  private String parseResponse(HttpResponse<String> response) {
    String responseBody = response.body();
    int statusCode = response.statusCode();
    if (statusCode < 200 || statusCode >= 300) {
      throw new RuntimeException("LLM HTTP " + statusCode + ": " + summarize(responseBody));
    }

    JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
    if (responseJson.has("error")) {
      JsonObject error = responseJson.getAsJsonObject("error");
      String message = error.has("message") ? error.get("message").getAsString() : error.toString();
      throw new RuntimeException(message);
    }

    JsonArray choices = responseJson.getAsJsonArray("choices");
    if (choices == null || choices.size() == 0) {
      throw new RuntimeException("LLM 响应中没有 choices: " + summarize(responseBody));
    }

    JsonObject firstChoice = choices.get(0).getAsJsonObject();
    if (firstChoice.has("message")) {
      JsonObject message = firstChoice.getAsJsonObject("message");
      if (message.has("content")) {
        String content = extractContent(message.get("content"));
        if (!content.isBlank()) {
          return content;
        }
      }
    }

    if (firstChoice.has("text")) {
      String text = firstChoice.get("text").getAsString().trim();
      if (!text.isBlank()) {
        return text;
      }
    }

    throw new RuntimeException("LLM 响应中没有可用文本: " + summarize(responseBody));
  }

  private String extractContent(JsonElement contentElement) {
    if (contentElement == null || contentElement.isJsonNull()) {
      return "";
    }
    if (contentElement.isJsonPrimitive()) {
      return contentElement.getAsString().trim();
    }
    if (contentElement.isJsonArray()) {
      StringBuilder builder = new StringBuilder();
      for (JsonElement partElement : contentElement.getAsJsonArray()) {
        if (!partElement.isJsonObject()) {
          continue;
        }
        JsonObject part = partElement.getAsJsonObject();
        if (part.has("text")) {
          builder.append(part.get("text").getAsString());
        }
      }
      return builder.toString().trim();
    }
    return contentElement.toString().trim();
  }

  private String summarize(String body) {
    if (body == null || body.isBlank()) {
      return "(empty body)";
    }
    String compact = body.replaceAll("\\s+", " ").trim();
    if (compact.length() <= 240) {
      return compact;
    }
    return compact.substring(0, 240) + "...";
  }

  private String normalizeApiUrl(String apiUrl) {
    String trimmed = apiUrl == null ? "" : apiUrl.trim();
    if (trimmed.endsWith("/chat/completions")) {
      return trimmed;
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed + "/chat/completions";
  }
}
