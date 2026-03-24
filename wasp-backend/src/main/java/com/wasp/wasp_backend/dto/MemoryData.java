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
  @Getter @Setter private double memory_usage_percent;
  @Getter @Setter private long page_fault_count;
  @Getter @Setter private String timestamp;

  public void addTotalBytes(long bytes) {this.total_bytes = this.total_bytes + bytes; }
  public void addFreeBytes(long bytes) {this.free_bytes = this.free_bytes + bytes; }
  public void addUsedBytes(long bytes) {this.used_bytes = this.used_bytes + bytes; }
  public void addMemUsage(double memory_usage_percent) {this.memory_usage_percent = this.memory_usage_percent + memory_usage_percent; }
  public void addPageFault(long page_fault) {this.page_fault_count = this.page_fault_count + page_fault; }
}
