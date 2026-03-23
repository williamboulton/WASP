package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Time;
import java.sql.Timestamp;

@ToString
public class DiskData {
  @Getter @Setter private String drive_letter;
  @Getter @Setter private long total_bytes;
  @Getter @Setter private long free_bytes;
  @Getter @Setter private int read_speed_bytes_per_sec;
  @Getter @Setter private int write_speed_bytes_per_sec;
  @Getter @Setter private String timestamp;

  public void addTotalBytes(long bytes) {this.total_bytes = this.total_bytes + bytes; }
  public void addFreeBytes(long bytes) {this.free_bytes = this.free_bytes + bytes; }
  public void addReadSpeed(int rsbps) {this.read_speed_bytes_per_sec = this.read_speed_bytes_per_sec + rsbps; }
  public void addWriteSpeed(int wsbps) {this.write_speed_bytes_per_sec = this.write_speed_bytes_per_sec + wsbps; }
}
