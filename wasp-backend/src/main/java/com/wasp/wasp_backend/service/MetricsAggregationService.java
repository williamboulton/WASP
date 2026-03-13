package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetricsAggregationService {
  // Number of samples we have currently aggregated
  private int sampleCount = 0;

  // list of cpu cores
  private List<CpuCoreData> cpuCores = new ArrayList<>();


  private final MetricRepository metricRepository;

  public MetricsAggregationService(MetricRepository metricRepository){
    this.metricRepository = metricRepository;
  }

}
