package com.wasp.wasp_backend.websocket;

/**
 * Data from the "data" field in the json payload are mapped to this class
 * @author Patrick Muller
 */
public class MetricData {

  private Double cpu;
  private Double memory;

  public Double getCpu() { return cpu; }
  public void setCpu(Double cpu) { this.cpu = cpu; }

  public Double getMemory() { return memory; }
  public void setMemory(Double memory) { this.memory = memory; }
}
