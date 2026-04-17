package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
public class ProcessData {
  @Getter @Setter private int pid;
  @Getter @Setter private String name;
  @Getter @Setter private String owner;
  @Getter @Setter private String priority;
  @Getter @Setter private double cpu_percent;
  @Getter @Setter private long cpu_time_100ns;
  @Getter @Setter private double mem_percent;
  @Getter @Setter private String location;
  @Getter @Setter private String timestamp;
}
