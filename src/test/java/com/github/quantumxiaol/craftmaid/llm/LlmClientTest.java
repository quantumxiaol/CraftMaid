package com.github.quantumxiaol.craftmaid.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LlmClientTest {
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
