package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@ToString
public class MemoryData {
  @Getter @Setter private long total_bytes;
  @Getter @Setter private long free_bytes;
  @Getter @Setter private long used_bytes;
  @Getter @Setter private int memory_usage_percent;
  @Getter @Setter private long page_fault_count;
  @Getter @Setter private String timestamp;
}
