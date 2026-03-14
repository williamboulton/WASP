package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetricsAggregationService {
  // Number of samples we have currently aggregated
  private int sampleCount = 0;

  private CpuData cpuData = new CpuData();

  // list of cpu cores
  private List<CpuCoreData> cpuCores = new ArrayList<>();


  private final MetricRepository metricRepository;

  public MetricsAggregationService(MetricRepository metricRepository){
    this.metricRepository = metricRepository;
  }

  public void ingest(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData, List<DiskData> diskData){
    this.cpuData.addMhz(cpuData.getCpu_mhz());
    this.cpuData.addCpuUsage(cpuData.getCpu_usage_percent());
    this.cpuData.addSystemResponse(cpuData.getSystem_responsiveness_percent());


    if (sampleCount == 0){
      this.cpuData.setTimestamp(cpuData.getTimestamp());
    }

    System.out.println(this.cpuData);
    sampleCount++;
  }
}
