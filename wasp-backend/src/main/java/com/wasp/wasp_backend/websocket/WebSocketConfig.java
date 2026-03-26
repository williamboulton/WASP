package com.wasp.wasp_backend.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * This class enables the MetricsWebSocketHandler class
 * and registers it under the /ws/metrics endpoint. It allows
 * all origins to request.
 * @author Patrick Muller
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final MetricsWebSocketHandler metricHandler;

  public WebSocketConfig(MetricsWebSocketHandler metricHandler) {
    this.metricHandler = metricHandler;
  }

  /**
   * This Bean allows messages of 2MB to be sent over the socket which we need
   * for our Windows service payloads
   */
  @Profile("!test")
  @Bean
  public ServletServerContainerFactoryBean createWebSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

    // Set the buffer for text messages to 2MB
    container.setMaxTextMessageBufferSize(2048 * 1024);

    // Set the buffer for binary messages to 2MB
    container.setMaxBinaryMessageBufferSize(2048 * 1024);

    return container;
  }

  /**
   * Add the MetricsWebSocketHandler to the metrics endpoint
   * and register it
   * @param registry WebSocketHandlerRegistry
   */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(metricHandler, "/ws/metrics")
      .setAllowedOrigins("*");
  }

}
