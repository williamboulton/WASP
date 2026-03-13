package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@ToString
public class MemoryData {
  @Getter @Setter long total_bytes;
  @Getter @Setter long free_bytes;
  @Getter @Setter long used_bytes;
  @Getter @Setter int memory_usage_percent;
  @Getter @Setter long page_fault_count;
  @Getter @Setter String timestamp;
}
