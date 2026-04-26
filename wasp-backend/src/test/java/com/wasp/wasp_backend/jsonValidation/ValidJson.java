package com.wasp.wasp_backend.jsonValidation;

import com.wasp.wasp_backend.repository.MetricRepository;
import com.wasp.wasp_backend.service.MetricsAggregationService;
import com.wasp.wasp_backend.websocket.MetricsWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidJson{

  @Mock
  private MetricsAggregationService metricsAggregationService;

  @Mock
  private WebSocketSession session;

  private ObjectMapper objectMapper;
  private MetricsWebSocketHandler handler;

  private MetricsAggregationService service() {
    return new MetricsAggregationService(mock(MetricRepository.class));
  }

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    handler = new MetricsWebSocketHandler(objectMapper, metricsAggregationService);
  }

  @Test
  void shouldProcessValidJson() throws Exception {
    String validJson = """
    {
      "cpu": {
        "cpu_mhz": 2400,
        "cpu_usage_percent": 50,
        "system_responsiveness_percent": 90,
        "timestamp": 123456
      },
      "cpu_cores": [
        {
          "core_index": 0,
          "core_usage_percent": 40,
          "timestamp": 123456
        }
      ],
      "memory": {
        "total_bytes": 1000,
        "free_bytes": 400,
        "used_bytes": 600,
        "memory_usage_percent": 60,
        "page_fault_count": 10,
        "timestamp": 123456
      },
      "disk": [
        {
          "drive_letter": "C",
          "total_bytes": 1000,
          "free_bytes": 500,
          "read_speed_bytes_per_sec": 100,
          "write_speed_bytes_per_sec": 200,
          "timestamp": 123456
        }
      ]
    }
    """;

    TextMessage message = new TextMessage(validJson);

    handler.handleTextMessage(session, message);

    verify(metricsAggregationService, times(1))
      .ingest(any(), any(), any(), any());
  }

  @Test
  void shouldSendErrorWhenMissingField() throws Exception {
    String invalidJson = """
    {
      "cpu": {
        "cpu_mhz": 2400
      },
      "cpu_cores": [],
      "memory": {},
      "disk": []
    }
    """;

    TextMessage message = new TextMessage(invalidJson);

    handler.handleTextMessage(session, message);

    verify(session).sendMessage(argThat(msg -> {
      String payload = ((TextMessage) msg).getPayload();
      return payload.contains("error") &&
        payload.contains("INVALID_JSON");
    }));

    verify(metricsAggregationService, never())
      .ingest(any(), any(), any(), any());
  }

  @Test
  void shouldHandleMalformedJson() throws Exception {
    String badJson = "{ this is not valid json ";

    TextMessage message = new TextMessage(badJson);

    handler.handleTextMessage(session, message);

    verify(session).sendMessage(argThat(msg -> {
      String payload = ((TextMessage) msg).getPayload();
      return payload.contains("INVALID_JSON");
    }));
  }
}
