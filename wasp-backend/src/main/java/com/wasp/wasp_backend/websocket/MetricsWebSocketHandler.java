package com.wasp.wasp_backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;

  public MetricsWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("C++ service connected: " + session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session,
                                   TextMessage message) throws Exception {

    String payload = message.getPayload();

    // 🔥 Parse JSON into Java object
    MetricMessage metricMessage =
      objectMapper.readValue(payload, MetricMessage.class);

    System.out.println("Type: " + metricMessage.getType());
    System.out.println("Source: " + metricMessage.getSource());
    System.out.println("CPU: " + metricMessage.getData().getCpu());
    System.out.println("Memory: " + metricMessage.getData().getMemory());
  }
}
