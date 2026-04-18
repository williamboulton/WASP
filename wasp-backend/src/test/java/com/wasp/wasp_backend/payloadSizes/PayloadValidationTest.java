package com.wasp.wasp_backend.payloadSizes;

import com.wasp.wasp_backend.websocket.MetricsWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PayloadValidationTest {

  @LocalServerPort
  private int port;

  @MockitoSpyBean
  private MetricsWebSocketHandler webSocketHandler;

  @Test
  void shouldInvokeHandlerWhenPayloadIsUnder2MB() throws Exception {
    StandardWebSocketClient client = new StandardWebSocketClient();
    WebSocketSession session = client.execute(
      new TextWebSocketHandler() {},
      new WebSocketHttpHeaders(),
      URI.create("ws://localhost:" + port + "/ws/metrics")
    ).get();

    String jsonPrefix = "{\"data\":\"";
    String jsonSuffix = "\"}";
    int targetSize = 2 * 1024 * 1024;
    int contentSize = targetSize - jsonPrefix.length() - jsonSuffix.length();
    String payload = jsonPrefix + "a".repeat(contentSize) + jsonSuffix;

    session.sendMessage(new TextMessage(payload));

    verify(webSocketHandler, timeout(2000).times(1))
      .handleTextMessage(any(WebSocketSession.class), any(TextMessage.class));
  }

  @Test
  void shouldNotInvokeHandlerWhenPayloadExceeds2MB() throws Exception {
    StandardWebSocketClient client = new StandardWebSocketClient();
    WebSocketSession session = client.execute(
      new TextWebSocketHandler() {},
      new WebSocketHttpHeaders(),
      URI.create("ws://localhost:" + port + "/ws/metrics")
    ).get();

    String jsonPrefix = "{\"data\":\"";
    String jsonSuffix = "\"}";
    int targetSize = (2 * 1024 * 1024) + 1;
    int contentSize = targetSize - jsonPrefix.length() - jsonSuffix.length();
    String payload = jsonPrefix + "a".repeat(contentSize) + jsonSuffix;

    try {
      session.sendMessage(new TextMessage(payload));
    } catch (Throwable ignored) {
    }

    verify(webSocketHandler, after(2000).never())
      .handleTextMessage(any(WebSocketSession.class), any(TextMessage.class));
  }
}
