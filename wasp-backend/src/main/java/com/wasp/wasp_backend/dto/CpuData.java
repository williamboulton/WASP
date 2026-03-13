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
}
