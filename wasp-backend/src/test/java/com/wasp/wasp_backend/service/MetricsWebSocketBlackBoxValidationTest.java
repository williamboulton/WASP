package com.wasp.wasp_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.WebSocket;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MetricsWebSocketBlackBoxValidationTest extends MetricsWebSocketBlackBoxTestSupport {

  @Test
  void websocketRejectsMalformedPayloadAndDoesNotWriteDatabaseRows() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadMissingCpuTimestamp(), true).join();

      assertTrue(
        waitForMessageContains(listener, "\"type\":\"error\"", 3000),
        "Expected websocket validation error message"
      );
      assertTrue(
        waitForMessageContains(listener, "\"code\":\"INVALID_JSON\"", 3000),
        "Expected INVALID_JSON error code for malformed payload"
      );

      assertNoRowsPersisted();
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketRejectsInvalidTimestampAndDoesNotWriteDatabaseRows() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadWithInvalidProcessTimestamp(), true).join();

      assertTrue(
        waitForMessageContains(listener, "\"type\":\"error\"", 3000),
        "Expected websocket validation error message"
      );
      assertTrue(
        waitForMessageContains(listener, "\"code\":\"INVALID_FORMAT\"", 3000),
        "Expected INVALID_FORMAT error code for invalid timestamp"
      );

      assertNoRowsPersisted();
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }
}
