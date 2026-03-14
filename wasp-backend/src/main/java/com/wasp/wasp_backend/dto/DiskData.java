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
}
