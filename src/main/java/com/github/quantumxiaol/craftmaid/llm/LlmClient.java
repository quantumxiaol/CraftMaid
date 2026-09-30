package com.github.quantumxiaol.craftmaid.llm;

import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LlmClient {
  private static final Logger LOGGER = Logger.getLogger("CraftMaid");
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
  private final int hardTimeoutSeconds;
  private final int transientRetryCount;
  private final int transientRetryDelayMillis;
  private volatile boolean responseFormatUnsupported;

  public LlmClient(
      String apiUrl,
      String apiKey,
      String modelName,
      double temperature,
      int maxTokens,
      int timeoutSeconds,
      int hardTimeoutSeconds,
      int transientRetryCount,
      int transientRetryDelayMillis) {
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    this.apiUrl = normalizeApiUrl(apiUrl);
    this.apiKey = apiKey;
    this.modelName = modelName;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    this.hardTimeoutSeconds = Math.max(1, hardTimeoutSeconds);
    this.transientRetryCount = Math.max(0, transientRetryCount);
    this.transientRetryDelayMillis = Math.max(0, transientRetryDelayMillis);
  }

  public CompletableFuture<String> askAiAsync(String systemPrompt, String userPrompt) {
    return askAiAsync(systemPrompt, List.of(ConversationMessage.user(userPrompt)));
  }

  public CompletableFuture<String> askAiAsync(
      String systemPrompt, List<ConversationMessage> conversationMessages) {
    return askAiAsync(systemPrompt, conversationMessages, this.maxTokens, this.temperature);
  }

  public CompletableFuture<String> askAiAsync(
      String systemPrompt,
      List<ConversationMessage> conversationMessages,
      int maxTokens,
      double temperature) {
    return askAiAsyncInternal(
        systemPrompt, conversationMessages, maxTokens, temperature, false, "chat");
  }

  public CompletableFuture<String> askJsonAsync(
      String systemPrompt,
      List<ConversationMessage> conversationMessages,
      int maxTokens,
      double temperature,
      boolean responseFormatJsonObject,
      String mode) {
    String safeMode = mode == null || mode.isBlank() ? "json" : mode;
    boolean sendResponseFormat = responseFormatJsonObject && !responseFormatUnsupported;
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(hardTimeoutSeconds);
    CompletableFuture<String> firstAttempt =
        askAiAsyncInternal(
            systemPrompt,
            conversationMessages,
            maxTokens,
            temperature,
            sendResponseFormat,
            safeMode,
            deadlineNanos);
    return firstAttempt
        .handle(
            (content, throwable) -> {
              if (throwable == null) {
                return CompletableFuture.completedFuture(content);
              }
              boolean unsupportedFormat =
                  sendResponseFormat && looksLikeUnsupportedResponseFormat(throwable);
              boolean emptyResponse =
                  unwrapCompletionException(throwable) instanceof EmptyResponseException;
              if (!unsupportedFormat && !emptyResponse) {
                return CompletableFuture.<String>failedFuture(throwable);
              }

              String retryPrompt = systemPrompt;
              if (unsupportedFormat) {
                responseFormatUnsupported = true;
                LOGGER.warning(
                    "当前 LLM 接口不支持 response_format=json_object，已自动重试并降级为 prompt-only JSON。");
              } else {
                LOGGER.warning("LLM 返回空正文，正在重试一次 prompt-only JSON（不执行任何动作）。");
                retryPrompt =
                    (systemPrompt == null ? "" : systemPrompt)
                        + "\n必须在最终回复中输出符合上述约定的 JSON 对象，不能只思考而不作答。"
                        + "闲聊也必须填写 chat 字段，无动作时 actions 为 []。";
              }
              return askAiAsyncInternal(
                  retryPrompt,
                  conversationMessages,
                  maxTokens,
                  temperature,
                  false,
                  safeMode + "-prompt",
                  deadlineNanos);
            })
        .thenCompose(future -> future);
  }

  private boolean looksLikeUnsupportedResponseFormat(Throwable throwable) {
    String message = errorDetectionText(throwable).toLowerCase(Locale.ROOT);
    boolean mentionsResponseFormat =
        message.contains("response_format") || message.contains("json_object");
    boolean unsupportedWording =
        message.contains("unsupported")
            || message.contains("unknown")
            || message.contains("unrecognized")
            || message.contains("not recognized")
            || message.contains("not support")
            || message.contains("not supported")
            || message.contains("invalid")
            || message.contains("invalid parameter")
            || message.contains("invalid_request");
    return mentionsResponseFormat && unsupportedWording;
  }

  private String errorDetectionText(Throwable throwable) {
    StringBuilder builder = new StringBuilder();
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor.getMessage() != null) {
        builder.append(cursor.getMessage()).append('\n');
      }
      if (cursor instanceof LlmApiException apiException) {
        builder.append(apiException.detectionText()).append('\n');
      }
      cursor = cursor.getCause();
    }
    return builder.toString();
  }

  private String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
  }

  public CompletableFuture<String> summarizeMemoryAsync(
      ConversationHistory.CompressionRequest request, int maxTokens, double temperature) {
    if (request == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("压缩请求不能为空"));
    }
    int safeMaxTokens = Math.max(200, maxTokens);
    double safeTemperature = Math.max(0.0, Math.min(2.0, temperature));
    return askAiAsyncInternal(
        MEMORY_COMPRESSION_SYSTEM_PROMPT,
        List.of(ConversationMessage.user(request.toPrompt())),
        safeMaxTokens,
        safeTemperature,
        false,
        "memory");
  }

  private CompletableFuture<String> askAiAsyncInternal(
      String systemPrompt,
      List<ConversationMessage> conversationMessages,
      int maxTokens,
      double temperature,
      boolean responseFormatJsonObject,
      String mode) {
    return askAiAsyncInternal(
        systemPrompt,
        conversationMessages,
        maxTokens,
        temperature,
        responseFormatJsonObject,
        mode,
        System.nanoTime() + TimeUnit.SECONDS.toNanos(hardTimeoutSeconds));
  }

  private CompletableFuture<String> askAiAsyncInternal(
      String systemPrompt,
      List<ConversationMessage> conversationMessages,
      int maxTokens,
      double temperature,
      boolean responseFormatJsonObject,
      String mode,
      long deadlineNanos) {
    long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    if (remainingMillis <= 0) {
      return CompletableFuture.failedFuture(
          new HttpTimeoutException("LLM retry deadline exceeded"));
    }
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
    if (responseFormatJsonObject) {
      JsonObject responseFormat = new JsonObject();
      responseFormat.addProperty("type", "json_object");
      payload.add("response_format", responseFormat);
    }

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
      if (conversationMessage.images().isEmpty()) {
        message.addProperty("content", conversationMessage.content());
      } else {
        JsonArray parts = new JsonArray();
        parts.add(textPart(conversationMessage.content()));
        for (var image : conversationMessage.images()) {
          parts.add(textPart(image.label()));
          JsonObject imageUrl = new JsonObject();
          imageUrl.addProperty("url", image.dataUrl());
          JsonObject part = new JsonObject();
          part.addProperty("type", "image_url");
          part.add("image_url", imageUrl);
          parts.add(part);
        }
        message.add("content", parts);
      }
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

    String requestId = UUID.randomUUID().toString().substring(0, 8);
    int imageCount =
        safeConversationMessages.stream()
            .filter(java.util.Objects::nonNull)
            .filter(ConversationMessage::isValid)
            .mapToInt(message -> message.images().size())
            .sum();
    if (imageCount > 0)
      LOGGER.info(
          "LLM vision request request_id=" + requestId + " mode=" + mode + " images=" + imageCount);
    long startedAtNanos = System.nanoTime();
    LOGGER.fine(
        "LLM request started request_id="
            + requestId
            + " mode="
            + mode
            + " model="
            + modelName
            + " hard_timeout="
            + hardTimeoutSeconds
            + "s");

    CompletableFuture<String> requestFuture =
        sendWithTransientRetry(request, mode, requestId, 1, transientRetryCount, deadlineNanos);
    return requestFuture
        .orTimeout(remainingMillis, TimeUnit.MILLISECONDS)
        .whenComplete(
            (ignored, throwable) -> {
              if (throwable == null || looksLikeUnsupportedResponseFormat(throwable)) {
                return;
              }
              long elapsedMillis =
                  TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
              LOGGER.warning(
                  "LLM request failed request_id="
                      + requestId
                      + " mode="
                      + mode
                      + " elapsed_ms="
                      + elapsedMillis
                      + " error="
                      + rootMessage(throwable));
            });
  }

  private CompletableFuture<String> sendWithTransientRetry(
      HttpRequest request,
      String mode,
      String requestId,
      int attempt,
      int retriesRemaining,
      long deadlineNanos) {
    if (System.nanoTime() >= deadlineNanos) {
      return CompletableFuture.failedFuture(
          new HttpTimeoutException("LLM retry deadline exceeded"));
    }
    CompletableFuture<String> attemptFuture =
        httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(
                response -> {
                  try {
                    return parseResponse(response, mode);
                  } catch (JsonParseException | IllegalStateException | NullPointerException ex) {
                    throw new RuntimeException("LLM 返回格式无法解析: " + summarize(response.body()), ex);
                  }
                });

    return attemptFuture
        .handle(
            (content, throwable) -> {
              if (throwable == null) {
                return CompletableFuture.completedFuture(content);
              }

              Throwable cause = unwrapCompletionException(throwable);
              if (retriesRemaining <= 0 || !isRetryableMode(mode) || !isTransientFailure(cause)) {
                return CompletableFuture.<String>failedFuture(cause);
              }
              long delayMillis =
                  Math.max(
                      transientRetryDelayMillis,
                      cause instanceof LlmApiException api ? api.retryAfterMillis : 0L);
              if (delayMillis >= TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())) {
                return CompletableFuture.<String>failedFuture(cause);
              }

              LOGGER.warning(
                  "LLM transient failure request_id="
                      + requestId
                      + " mode="
                      + mode
                      + " attempt="
                      + attempt
                      + " retrying_in_ms="
                      + delayMillis
                      + " error="
                      + rootMessage(cause));
              return CompletableFuture.supplyAsync(
                      () -> null,
                      CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS))
                  .thenCompose(
                      ignored ->
                          sendWithTransientRetry(
                              request,
                              mode,
                              requestId,
                              attempt + 1,
                              retriesRemaining - 1,
                              deadlineNanos));
            })
        .thenCompose(future -> future);
  }

  private boolean isRetryableMode(String mode) {
    return mode == null
        || mode.equals("chat")
        || mode.equals("plan")
        || mode.equals("memory")
        || mode.startsWith("plan-");
  }

  private static JsonObject textPart(String text) {
    JsonObject part = new JsonObject();
    part.addProperty("type", "text");
    part.addProperty("text", text);
    return part;
  }

  /** Retry only a rejected image payload as text, never authentication/network failures. */
  public boolean isImageInputRejected(Throwable throwable) {
    for (Throwable cursor = throwable; cursor != null; cursor = cursor.getCause()) {
      if (!(cursor instanceof LlmApiException error)) continue;
      if (error.statusCode() == 413 || error.statusCode() == 415) return true;
      if (error.statusCode() != 400 && error.statusCode() != 422) return false;
      String text = error.detectionText().toLowerCase(Locale.ROOT);
      return text.contains("image")
          || text.contains("vision")
          || text.contains("multimodal")
          || text.contains("content must be a string")
          || text.contains("图片")
          || text.contains("图像");
    }
    return false;
  }

  private boolean isTransientFailure(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor instanceof SocketException
          || cursor instanceof ConnectException
          || cursor instanceof HttpTimeoutException
          || cursor instanceof EOFException) {
        return true;
      }
      if (cursor instanceof LlmApiException apiException) {
        int statusCode = apiException.statusCode();
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  private Throwable unwrapCompletionException(Throwable throwable) {
    Throwable cursor = throwable;
    while ((cursor instanceof CompletionException) && cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor;
  }

  private String parseResponse(HttpResponse<String> response, String mode) {
    String responseBody = response.body();
    int statusCode = response.statusCode();
    if (statusCode < 200 || statusCode >= 300) {
      throw new LlmApiException(
          "LLM HTTP " + statusCode + ": " + summarize(responseBody),
          responseBody,
          statusCode,
          retryAfterMillis(response.headers().firstValue("Retry-After").orElse("")));
    }

    JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
    if (responseJson.has("error")) {
      JsonObject error = responseJson.getAsJsonObject("error");
      String message = error.has("message") ? error.get("message").getAsString() : error.toString();
      throw new LlmApiException(summarize(message), responseBody, statusCode, 0L);
    }

    JsonArray choices = responseJson.getAsJsonArray("choices");
    if (choices == null || choices.size() == 0) {
      throw new RuntimeException("LLM 响应中没有 choices: " + summarize(responseBody));
    }

    JsonObject firstChoice = choices.get(0).getAsJsonObject();
    String finishReason =
        firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()
            ? firstChoice.get("finish_reason").getAsString()
            : "";
    logUsage(responseJson, mode, finishReason);
    boolean hasReasoningContent = false;
    if (firstChoice.has("message")) {
      JsonObject message = firstChoice.getAsJsonObject("message");
      if (message.has("content")) {
        String content = extractContent(message.get("content"));
        if (!content.isBlank()) {
          return content;
        }
      }
      hasReasoningContent =
          message.has("reasoning_content")
              && !extractContent(message.get("reasoning_content")).isBlank();
    }

    if (firstChoice.has("text")) {
      String text = firstChoice.get("text").getAsString().trim();
      if (!text.isBlank()) {
        return text;
      }
    }

    // Reasoning is neither a player reply nor an executable plan. Log metadata only.
    throw new EmptyResponseException(finishReason, hasReasoningContent);
  }

  private static final class EmptyResponseException extends RuntimeException {
    private EmptyResponseException(String finishReason, boolean hasReasoningContent) {
      super(
          "LLM 返回空正文: finish_reason="
              + (finishReason.isBlank() ? "unknown" : finishReason)
              + " reasoning_present="
              + hasReasoningContent);
    }
  }

  private void logUsage(JsonObject responseJson, String mode, String finishReason) {
    if (responseJson == null || !responseJson.has("usage")) {
      return;
    }
    JsonObject usage = responseJson.getAsJsonObject("usage");
    int hit = intOrZero(usage, "prompt_cache_hit_tokens");
    int miss = intOrZero(usage, "prompt_cache_miss_tokens");
    int prompt = intOrZero(usage, "prompt_tokens");
    int completion = intOrZero(usage, "completion_tokens");
    if (hit <= 0 && miss <= 0 && completion <= 0 && finishReason.isBlank()) {
      return;
    }

    int promptDenominator = hit + miss;
    double hitRate = promptDenominator <= 0 ? 0.0 : 100.0 * hit / promptDenominator;
    String lengthWarning = "length".equalsIgnoreCase(finishReason) ? " finish_reason=length" : "";
    LOGGER.info(
        String.format(
            "LLM mode=%s cache_hit=%d cache_miss=%d hit_rate=%.1f%% prompt=%d out=%d%s",
            mode, hit, miss, hitRate, prompt, completion, lengthWarning));
  }

  private int intOrZero(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return 0;
    }
    try {
      return object.get(key).getAsInt();
    } catch (RuntimeException ex) {
      return 0;
    }
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
    String compact =
        body.replaceAll("data:image/[^;\\s\"]+;base64,[A-Za-z0-9+/=\\r\\n]+", "<image omitted>")
            .replaceAll("\\s+", " ")
            .trim();
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

  static long retryAfterMillis(String value) {
    try {
      return Math.multiplyExact(Math.max(0L, Long.parseLong(value.trim())), 1000L);
    } catch (NumberFormatException ex) {
      try {
        return Math.max(
            0L,
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                - System.currentTimeMillis());
      } catch (DateTimeParseException ignored) {
        return 0L;
      }
    } catch (ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  private static final class LlmApiException extends RuntimeException {
    private final String detectionText;
    private final int statusCode;
    private final long retryAfterMillis;

    private LlmApiException(
        String message, String detectionText, int statusCode, long retryAfterMillis) {
      super(message);
      this.detectionText = detectionText == null ? "" : detectionText;
      this.statusCode = statusCode;
      this.retryAfterMillis = retryAfterMillis;
    }

    private String detectionText() {
      return detectionText;
    }

    private int statusCode() {
      return statusCode;
    }
  }
}
