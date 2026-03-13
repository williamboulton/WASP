package com.wasp.wasp_backend.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;

@ToString
public class CpuCoreData {
  @Getter @Setter private int core_index;
  @Getter @Setter private double core_mhz;
  @Getter @Setter private double core_usage_percent;
  @Getter @Setter private String timestamp;

}
