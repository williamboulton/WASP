package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Time;
import java.sql.Timestamp;

@ToString
public class DiskData {
  @Getter @Setter String drive_letter;
  @Getter @Setter long total_bytes;
  @Getter @Setter long free_bytes;
  @Getter @Setter int read_speed_bytes_per_sec;
  @Getter @Setter int write_speed_bytes_per_sec;
  @Getter @Setter String timestamp;
}
