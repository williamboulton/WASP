package com.wasp.wasp_backend.event;

public record BackendNotificationEvent(
  String severity,
  String category,
  String title,
  String message
) {
}
