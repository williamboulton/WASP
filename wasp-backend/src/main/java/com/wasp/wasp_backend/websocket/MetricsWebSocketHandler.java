package com.wasp.wasp_backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;

  public MetricsWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

/*
  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("C++ service connected: " + session.getId());
  }
*/

  private void sendError(WebSocketSession session,
                         String errorCode,
                         String message) throws Exception {

    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("type", "error");
    errorResponse.put("code", errorCode);
    errorResponse.put("message", message);

    String json = objectMapper.writeValueAsString(errorResponse);

    session.sendMessage(new TextMessage(json));
  }

  private void validateMetricMessage(MetricMessage message) {

    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }

    if (message.getType() == null || message.getType().isBlank()) {
      throw new IllegalArgumentException("Field 'type' is required");
    }

    if (message.getSource() == null || message.getSource().isBlank()) {
      throw new IllegalArgumentException("Field 'source' is required");
    }

    if (message.getData() == null) {
      throw new IllegalArgumentException("Field 'data' is required");
    }

    MetricData data = message.getData();

    if (data.getCpu() == null) {
      throw new IllegalArgumentException("Field 'data.cpu' is required");
    }

    if (data.getMemory() == null) {
      throw new IllegalArgumentException("Field 'data.memory' is required");
    }
  }


  @Override
  protected void handleTextMessage(WebSocketSession session,
                                   TextMessage message) throws Exception {

    try {
      MetricMessage metricMessage =
        objectMapper.readValue(message.getPayload(), MetricMessage.class);

      validateMetricMessage(metricMessage);

      // Safe to use now
      System.out.println("CPU: " + metricMessage.getData().getCpu());
      System.out.println("Memory: " + metricMessage.getData().getMemory());

    } catch (IllegalArgumentException e) {
      sendError(session, "INVALID_FORMAT", e.getMessage());
    } catch (Exception e) {
      sendError(session, "INVALID_JSON", "Malformed JSON payload");
    }
  }


}
