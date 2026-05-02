package com.wasp.wasp_backend.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
public class HistoryData {

  @Getter @Setter private Double avgCpuGhz;
  @Getter @Setter private Double avgCpuUsage;

  @Getter @Setter private Double avgCoreGhz;
  @Getter @Setter private Double avgCoreUsage;

  @Getter @Setter private Double avgTotalMem;
  @Getter @Setter private Double avgFreeMem;
  @Getter @Setter private Double avgUsedMem;
  @Getter @Setter private Double avgMemUsage;
  @Getter @Setter private Double avgPageFaults;

  @Getter @Setter private Double avgTotalDiskSpace;
  @Getter @Setter private Double avgFreeDiskSpace;
  @Getter @Setter private Double avgReadSpeed;
  @Getter @Setter private Double avgWriteSpeed;

}
