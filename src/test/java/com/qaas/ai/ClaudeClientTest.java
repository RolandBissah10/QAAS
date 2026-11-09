package com.qaas.ai;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ClaudeClientTest {

    @Test
    void buildsGenerationEndpointFromConfiguredModel() {
        assertThat(ClaudeClient.geminiUrl("gemini-3.5-flash-lite", "test-key"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=test-key");
    }

    @Test
    void retriesGeminiOnceForTransientRateLimitOrUnavailableResponses() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.test")).GET().build();
        HttpResponse<String> rateLimited = responseWithStatus(429);
        HttpResponse<String> success = responseWithStatus(200);
        doReturn(rateLimited, success).when(client).send(any(HttpRequest.class), any());

        HttpResponse<String> response = ClaudeClient.sendGeminiWithRetry(client, request, Duration.ZERO);

        assertThat(response.statusCode()).isEqualTo(200);
        verify(client, org.mockito.Mockito.times(2)).send(any(HttpRequest.class), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void retriesGeminiOnceForServiceUnavailableResponses() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.test")).GET().build();
        HttpResponse<String> unavailable = responseWithStatus(503);
        HttpResponse<String> success = responseWithStatus(200);
        doReturn(unavailable, success).when(client).send(any(HttpRequest.class), any());

        HttpResponse<String> response = ClaudeClient.sendGeminiWithRetry(client, request, Duration.ZERO);

        assertThat(response.statusCode()).isEqualTo(200);
        verify(client, org.mockito.Mockito.times(2)).send(any(HttpRequest.class), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void doesNotRetryOtherErrorResponses() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.test")).GET().build();
        HttpResponse<String> serverError = responseWithStatus(500);
        doReturn(serverError).when(client).send(any(HttpRequest.class), any());

        HttpResponse<String> response = ClaudeClient.sendGeminiWithRetry(client, request, Duration.ZERO);

        assertThat(response.statusCode()).isEqualTo(500);
        verify(client).send(any(HttpRequest.class), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void doesNotRetryTimedOutRequests() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.test")).GET().build();
        doThrow(new HttpTimeoutException("timed out")).when(client).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> ClaudeClient.sendGeminiWithRetry(client, request, Duration.ZERO))
                .isInstanceOf(HttpTimeoutException.class);
        verify(client).send(any(HttpRequest.class), any());
        verifyNoMoreInteractions(client);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> responseWithStatus(int statusCode) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        return response;
    }
}
