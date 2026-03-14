package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@ToString
public class CpuData {
  @Getter @Setter private double cpu_mhz;
  @Getter @Setter private double cpu_usage_percent;
  @Getter @Setter private double system_responsiveness_percent;
  @Getter @Setter private String timestamp;

  public void addMhz(Double cpu_mhz) {
    this.cpu_mhz = this.cpu_mhz + cpu_mhz;
  }

  public void addCpuUsage(Double cpu_usage_percent) { this.cpu_usage_percent = this.cpu_usage_percent + cpu_usage_percent; }

  public void addSystemResponse(Double system_responsiveness_percent) {
    this.system_responsiveness_percent = this.system_responsiveness_percent + system_responsiveness_percent;
  }
}
