package com.wasp.wasp_backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * This class enables the MetricsWebSocketHandler class
 * and registers it under the /ws/metrics endpoint. It allows
 * all origins to request.
 * @author Patrick Muller
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final MetricsWebSocketHandler handler;

  public WebSocketConfig(MetricsWebSocketHandler handler) {
    this.handler = handler;
  }

  /**
   * Add the MetricsWebSocketHandler to the metrics endpoint
   * and register it
   * @param registry WebSocketHandlerRegistry
   */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/metrics")
      .setAllowedOrigins("*");
  }

}
