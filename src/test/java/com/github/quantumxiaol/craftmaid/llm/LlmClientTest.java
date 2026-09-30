package com.github.quantumxiaol.craftmaid.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.conversation.ConversationImage;
import com.github.quantumxiaol.craftmaid.conversation.ConversationMessage;
import com.github.quantumxiaol.craftmaid.intent.MaidActionPlanParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class LlmClientTest {
  @ParameterizedTest
  @CsvSource({
    "https://api.deepseek.com,deepseek-v4-flash,auto,disabled",
    "https://api.deepseek.com/v1,deepseek-flash,auto,disabled",
    "https://api.deepseek.com,deepseek-v4-pro,auto,disabled",
    "https://api.deepseek.com,deepseek-reasoner,auto,absent",
    "https://api.deepseek.com,deepseek-flash,enabled,enabled",
    "https://api.deepseek.com,deepseek-flash,provider,absent",
    "https://example.com,deepseek-flash,auto,absent",
    "https://api.deepseek.com.example.com,deepseek-flash,auto,absent",
    "https://example.com,deepseek-flash,disabled,disabled"
  })
  void thinkingControlIsExplicitAndAutoOnlyAppliesToOfficialDeepSeek(
      String url, String model, String mode, String expected) throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var success = response(200, "{\"choices\":[{\"message\":{\"content\":\"收到\"}}]}", "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(CompletableFuture.completedFuture(success));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient(url, "", model, .2, 50, 2, 3, 0, 0, mode);
      assertEquals(
          "收到",
          client.askFinalAsync("reply", List.of(ConversationMessage.user("你好")), 50, .2).join());
      var requests = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http)
          .sendAsync(
              requests.capture(),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
      var payload = body(requests.getValue());
      assertFalse(payload.has("response_format"));
      if (expected.equals("absent")) assertFalse(payload.has("thinking"));
      else assertEquals(expected, payload.getAsJsonObject("thinking").get("type").getAsString());
    }
  }

  @Test
  void finalRetriesEmptyContentAsPlainTextKeepingTheSameImages() throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var empty =
        response(
            200,
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" \",\"reasoning_content\":\"private\"}}]}",
            "");
    var success =
        response(200, "{\"choices\":[{\"message\":{\"content\":\"主人，我看见铁块墙和玻璃窗。\"}}]}", "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            CompletableFuture.completedFuture(empty), CompletableFuture.completedFuture(success));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client =
          new LlmClient("https://api.deepseek.com", "", "deepseek-v4-flash", .2, 50, 2, 3, 0, 0);
      var images =
          List.of("NORTH", "EAST", "SOUTH", "WEST").stream()
              .map(label -> ConversationImage.png(label, new byte[] {1, 2, 3}))
              .toList();
      assertEquals(
          "主人，我看见铁块墙和玻璃窗。",
          client
              .askFinalAsync("直接回复", List.of(ConversationMessage.user("这是什么建筑", images)), 50, .2)
              .join());
      var requests = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http, times(2))
          .sendAsync(
              requests.capture(),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
      var first = body(requests.getAllValues().getFirst());
      var retry = body(requests.getAllValues().getLast());
      assertFalse(first.has("response_format"));
      assertFalse(retry.has("response_format"));
      assertEquals(
          first.getAsJsonArray("messages").get(1), retry.getAsJsonArray("messages").get(1));
      assertEquals(
          9,
          retry
              .getAsJsonArray("messages")
              .get(1)
              .getAsJsonObject()
              .getAsJsonArray("content")
              .size());
      assertFalse(retry.toString().contains("private"));
    }
  }

  @ParameterizedTest
  @CsvSource({"false,true", "false,false", "true,true", "true,false"})
  void planRepairsInvalidContentAtMostOnceAndValidatesTheRepair(
      boolean firstEmpty, boolean repairValid) {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    String valid = "{\"chat\":\"\",\"actions\":[{\"type\":\"FOLLOW_START\"}]}";
    JsonObject message = new JsonObject();
    message.addProperty("content", firstEmpty ? " " : "主人，我可以跟随你。");
    var first = response(200, "{\"choices\":[{\"message\":" + message + "}]}", "");
    message.addProperty(
        "content",
        repairValid ? valid : "{\"chat\":\"\",\"actions\":[{\"type\":\"RUN_COMMAND\"}]}");
    var second = response(200, "{\"choices\":[{\"message\":" + message + "}]}", "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 2, 0);
      var future =
          client.askJsonAsync(
              "JSON protocol",
              List.of(ConversationMessage.user("跟着我")),
              50,
              .2,
              true,
              "plan",
              new MaidActionPlanParser()::isValidPlan);
      if (repairValid) assertEquals(valid, future.join());
      else {
        var failure = assertThrows(CompletionException.class, future::join);
        assertTrue(failure.getCause().getMessage().contains("chat/actions"));
        assertFalse(failure.getCause().getMessage().contains("RUN_COMMAND"));
      }
      verify(http, times(2))
          .sendAsync(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
  }

  @ParameterizedTest
  @CsvSource({"plan,true", "plan,false", "final,true", "final,false"})
  void emptyJsonReplyRetriesOnceAndRemembersBrokenJsonModeWithoutLosingImages(
      String mode, boolean jsonFormat) throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var empty =
        response(
            200,
            """
        {"choices":[{"finish_reason":"stop","message":{"content":" ",
        "reasoning_content":"private reasoning, not a reply"}}]}
        """,
            "");
    var accepted =
        response(
            200,
            """
        {"choices":[{"message":{"content":"{\\"chat\\":\\"收到\\",\\"actions\\":[]}"}}]}
        """,
            "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            CompletableFuture.completedFuture(empty), CompletableFuture.completedFuture(accepted));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 0, 0);
      var messages =
          List.of(
              ConversationMessage.user(
                  "我喜欢你", List.of(ConversationImage.png("NORTH", new byte[] {1, 2, 3}))));
      String reply = client.askJsonAsync("JSON system", messages, 50, .2, jsonFormat, mode).join();
      assertEquals("收到", JsonParser.parseString(reply).getAsJsonObject().get("chat").getAsString());
      client.askJsonAsync("JSON system", messages, 50, .2, true, mode).join();
      var requests = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http, times(3))
          .sendAsync(
              requests.capture(),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
      var first = body(requests.getAllValues().get(0));
      var retry = body(requests.getAllValues().get(1));
      assertEquals(jsonFormat, first.has("response_format"));
      assertFalse(retry.has("response_format"));
      assertEquals(!jsonFormat, body(requests.getAllValues().get(2)).has("response_format"));
      assertEquals(
          first.getAsJsonArray("messages").get(1), retry.getAsJsonArray("messages").get(1));
      assertFalse(retry.toString().contains("private reasoning"));
    }
  }

  @ParameterizedTest
  @CsvSource({"stop,true", "length,true", "stop,false"})
  void repeatedEmptyJsonReplyStopsAfterOneRetryAndLogsMetadataOnly(
      String finishReason, boolean reasoning) {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var empty =
        response(
            200,
            "{\"choices\":[{\"finish_reason\":\""
                + finishReason
                + "\",\"message\":{\"content\":\" \",\"reasoning_content\":\""
                + (reasoning ? "secret reasoning" : "")
                + "\"}}]}",
            "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(CompletableFuture.completedFuture(empty));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 2, 0);
      var failure =
          assertThrows(
              CompletionException.class,
              () ->
                  client
                      .askJsonAsync(
                          "JSON", List.of(ConversationMessage.user("hello")), 50, .2, true, "plan")
                      .join());
      assertTrue(failure.getCause().getMessage().contains("finish_reason=" + finishReason));
      assertTrue(failure.getCause().getMessage().contains("reasoning_present=" + reasoning));
      assertFalse(failure.getCause().getMessage().contains("secret reasoning"));
      verify(http, times(2))
          .sendAsync(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
  }

  @Test
  void multimodalFinalKeepsImagesThroughJsonFormatFallbackButNotNextChat() throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var rejected = response(400, "unsupported response_format json_object", "");
    var accepted = response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            CompletableFuture.completedFuture(rejected),
            CompletableFuture.completedFuture(accepted));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 0, 0);
      var images =
          List.of("NORTH", "EAST", "SOUTH", "WEST").stream()
              .map(label -> ConversationImage.png(label, new byte[] {1, 2, 3}))
              .toList();
      assertEquals(
          "ok",
          client
              .askJsonAsync(
                  "system",
                  List.of(
                      ConversationMessage.assistant("old"),
                      ConversationMessage.user("同一份女仆观察", images)),
                  50,
                  .2,
                  true,
                  "final")
              .join());
      assertEquals("ok", client.askAiAsync("system", "普通聊天").join());
      var requests = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http, times(3))
          .sendAsync(
              requests.capture(),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
      for (int attempt = 0; attempt < 2; attempt++) {
        JsonObject body = body(requests.getAllValues().get(attempt));
        assertEquals(attempt == 0, body.has("response_format"));
        var messages = body.getAsJsonArray("messages");
        assertTrue(
            messages.get(0).getAsJsonObject().get("content").getAsString().startsWith("system"));
        assertEquals("old", messages.get(1).getAsJsonObject().get("content").getAsString());
        var content = messages.get(2).getAsJsonObject().getAsJsonArray("content");
        assertEquals(9, content.size());
        assertEquals("同一份女仆观察", content.get(0).getAsJsonObject().get("text").getAsString());
        for (int i = 0; i < 4; i++) {
          assertEquals(
              images.get(i).label(),
              content.get(i * 2 + 1).getAsJsonObject().get("text").getAsString());
          var part = content.get(i * 2 + 2).getAsJsonObject();
          assertEquals("image_url", part.get("type").getAsString());
          assertEquals(
              images.get(i).dataUrl(), part.getAsJsonObject("image_url").get("url").getAsString());
        }
      }
      String chat = body(requests.getAllValues().get(2)).toString();
      assertFalse(chat.contains("image_url"));
      assertFalse(chat.contains("base64"));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "400,unsupported image_url,true",
    "422,vision not supported,true",
    "413,too big,true",
    "401,invalid image auth,false",
    "503,image busy,false",
    "400,invalid max_tokens,false"
  })
  void recognizesImageRejectionWithoutHidingOtherFailures(
      int status, String error, boolean rejected) {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    var rejectedResponse = response(status, error, "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(CompletableFuture.completedFuture(rejectedResponse));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 0, 0);
      var failure =
          assertThrows(
              CompletionException.class, () -> client.askAiAsync("system", "hello").join());
      assertEquals(rejected, client.isImageInputRejected(failure));
    }
  }

  private static JsonObject body(HttpRequest request) throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    var completed = new CompletableFuture<Void>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
              public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              public void onNext(java.nio.ByteBuffer buffer) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                bytes.writeBytes(data);
              }

              public void onError(Throwable error) {
                completed.completeExceptionally(error);
              }

              public void onComplete() {
                completed.complete(null);
              }
            });
    completed.get(2, TimeUnit.SECONDS);
    return JsonParser.parseString(bytes.toString(java.nio.charset.StandardCharsets.UTF_8))
        .getAsJsonObject();
  }

  @ParameterizedTest
  @CsvSource({"429,0,true", "429,999,false", "503,0,true", "401,0,false"})
  void transientFailuresRetryWithinBudget(int status, String retryAfter, boolean shouldRetry)
      throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(HttpClient.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(http);
    HttpResponse<String> failure = response(status, "busy", retryAfter);
    HttpResponse<String> success =
        response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "");
    when(http.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(
            CompletableFuture.completedFuture(failure), CompletableFuture.completedFuture(success));
    try (var clients = mockStatic(HttpClient.class)) {
      clients.when(HttpClient::newBuilder).thenReturn(builder);
      var client = new LlmClient("http://localhost/v1", "", "test", .2, 50, 2, 3, 1, 0);
      var request = client.askAiAsync("test", "hello");
      if (shouldRetry) assertEquals("ok", request.get(2, TimeUnit.SECONDS));
      else assertThrows(CompletionException.class, request::join);
      verify(http, times(shouldRetry ? 2 : 1))
          .sendAsync(
              any(HttpRequest.class),
              org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> response(int status, String body, String retryAfter) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    when(response.headers())
        .thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of(retryAfter)), (a, b) -> true));
    return response;
  }
}
