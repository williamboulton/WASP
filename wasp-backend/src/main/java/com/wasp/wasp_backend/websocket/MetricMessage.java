package com.wasp.wasp_backend.websocket;

public class MetricMessage {

  private String type;
  private String source;
  private MetricData data;

  // Getters & Setters
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public MetricData getData() { return data; }
  public void setData(MetricData data) { this.data = data; }
}
