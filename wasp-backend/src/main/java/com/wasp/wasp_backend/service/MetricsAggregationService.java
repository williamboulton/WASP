package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsAggregationService {

  // Size of aggregated metric window, 60 samples
  private static final int WINDOW_SIZE = 60;

  private int sampleCount = 0;

  private final CpuData runningCpuTotals = new CpuData();

  private List<CpuCoreData> runningCoreTotals;

  private final MemoryData runningMemTotals = new MemoryData();

  private List<DiskData> runningDiskTotals;

  private MetricRepository metricRepository;

  public MetricsAggregationService(MetricRepository metricRepository) {
    this.metricRepository = metricRepository;
  }

  /**
   * Reset running totals and update timestamps
   *
   * @param cpuData
   * @param cpuCoreData
   * @param memoryData
   * @param diskData
   * @author Patrick Muller
   */
  private void resetState(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData, List<DiskData> diskData) {
    // clear cpu data
    this.runningCpuTotals.setCpu_mhz(0);
    this.runningCpuTotals.setCpu_usage_percent(0);
    this.runningCpuTotals.setSystem_responsiveness_percent(0);
    this.runningCpuTotals.setTimestamp(cpuData.getTimestamp());

    // clear cpu core data
    for (int i = 0; i < runningCoreTotals.size(); i++) {
      CpuCoreData runningCore = this.runningCoreTotals.get(i);
      CpuCoreData currCore = cpuCoreData.get(i);

      runningCore.setCore_index(i);
      runningCore.setCore_mhz(0);
      runningCore.setCore_usage_percent(0);
      runningCore.setTimestamp(currCore.getTimestamp());
    }

    // clear mem data
    this.runningMemTotals.setTotal_bytes(0);
    this.runningMemTotals.setFree_bytes(0);
    this.runningMemTotals.setUsed_bytes(0);
    this.runningMemTotals.setMemory_usage_percent(0);
    this.runningMemTotals.setPage_fault_count(0);
    this.runningMemTotals.setTimestamp(memoryData.getTimestamp());

    // clear disk data
    for (int i = 0; i < runningDiskTotals.size(); i++) {
      DiskData runningDisk = this.runningDiskTotals.get(i);
      DiskData currDisk = diskData.get(i);

      runningDisk.setDrive_letter(currDisk.getDrive_letter());
      runningDisk.setTotal_bytes(0);
      runningDisk.setFree_bytes(0);
      runningDisk.setRead_speed_bytes_per_sec(0);
      runningDisk.setWrite_speed_bytes_per_sec(0);
      runningDisk.setTimestamp(currDisk.getTimestamp());

    }
  }

  /**
   * Accumulate metrics with new payload
   *
   * @param cpuData
   * @param cpuCoreData
   * @param memoryData
   * @param diskData
   * @author Patrick Muller
   */
  private void accumulate(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData, List<DiskData> diskData) {
    // add cpu data to running totals
    runningCpuTotals.addMhz(cpuData.getCpu_mhz());
    runningCpuTotals.addCpuUsage(cpuData.getCpu_usage_percent());
    runningCpuTotals.addSystemResponse(cpuData.getSystem_responsiveness_percent());

    // add core data to running totals
    for (int i = 0; i < runningCoreTotals.size(); i++) {
      // get the aggregated core and core data we're adding
      CpuCoreData runningCore = runningCoreTotals.get(i);
      CpuCoreData newCore = cpuCoreData.get(i);

      runningCore.addMhz(newCore.getCore_mhz());
      runningCore.addUsage(newCore.getCore_usage_percent());
    }

    // add mem data to running totals
    runningMemTotals.addTotalBytes(memoryData.getTotal_bytes());
    runningMemTotals.addFreeBytes(memoryData.getFree_bytes());
    runningMemTotals.addUsedBytes(memoryData.getUsed_bytes());
    runningMemTotals.addMemUsage(memoryData.getMemory_usage_percent());
    runningMemTotals.addPageFault(memoryData.getPage_fault_count());

    // add disk data to running totals
    for (int i = 0; i < runningDiskTotals.size(); i++) {
      // get the aggregated disk and disk data we're adding
      DiskData runningDisk = runningDiskTotals.get(i);
      DiskData currDisk = diskData.get(i);

      runningDisk.addTotalBytes(currDisk.getTotal_bytes());
      runningDisk.addFreeBytes(currDisk.getFree_bytes());
      runningDisk.addReadSpeed(currDisk.getRead_speed_bytes_per_sec());
      runningDisk.addWriteSpeed(currDisk.getWrite_speed_bytes_per_sec());
    }
  }

  /**
   * For now, log window
   * TODO: Emit accumulated metrics to database
   *
   * @author Patrick Muller
   */
  private void emitAggregatedMetrics() {
    Map<String, Object> root = new HashMap<>();

    // CPU
    Map<String, Object> cpu = new HashMap<>();
    cpu.put("cpu_mhz", avg(runningCpuTotals.getCpu_mhz()));
    cpu.put("cpu_usage_percent", avg(runningCpuTotals.getCpu_usage_percent()));
    cpu.put("system_responsiveness_percent", avg(runningCpuTotals.getSystem_responsiveness_percent()));
    cpu.put("timestamp", runningCpuTotals.getTimestamp());

    root.put("cpu", cpu);

    List<Map<String, Object>> cores = new ArrayList<>();
    for (CpuCoreData core : runningCoreTotals) {
      Map<String, Object> coreMap = new HashMap<>();
      coreMap.put("core_index", core.getCore_index());
      coreMap.put("core_mhz", avg(core.getCore_mhz()));
      coreMap.put("core_usage_percent", avg(core.getCore_usage_percent()));
      coreMap.put("timestamp", core.getTimestamp());
      cores.add(coreMap);
    }

    root.put("cpu_cores", cores);

    // Memory
    Map<String, Object> memory = new HashMap<>();
    memory.put("total_bytes", avg((double) runningMemTotals.getTotal_bytes()));
    memory.put("free_bytes", avg((double) runningMemTotals.getFree_bytes()));
    memory.put("used_bytes", avg((double) runningMemTotals.getUsed_bytes()));
    memory.put("memory_usage_percent", avg(runningMemTotals.getMemory_usage_percent()));
    memory.put("page_fault_count", avg((double) runningMemTotals.getPage_fault_count()));
    memory.put("timestamp", runningMemTotals.getTimestamp());

    root.put("memory", memory);

    // Disk
    List<Map<String, Object>> disks = new ArrayList<>();
    for (DiskData disk : runningDiskTotals) {
      Map<String, Object> diskMap = new HashMap<>();
      diskMap.put("drive", disk.getDrive_letter());
      diskMap.put("total_bytes", avg((double) disk.getTotal_bytes()));
      diskMap.put("free_bytes", avg((double) disk.getFree_bytes()));
      diskMap.put("read_speed", avg(disk.getRead_speed_bytes_per_sec()));
      diskMap.put("write_speed", avg(disk.getWrite_speed_bytes_per_sec()));
      diskMap.put("timestamp", disk.getTimestamp());
      disks.add(diskMap);
    }

    root.put("disks", disks);

    // Write JSON file
    try {
      new JsonMapper();
      JsonMapper mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT) // pretty print
        .build();

      File outputFile = new File("output/", "output.json");
      outputFile.getParentFile().mkdirs();
      mapper.writeValue(outputFile, root);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Average out accumulated metrics by the sample count and round to nearest hundredth
   *
   * @param total Accumulated metrics
   * @return Averaged value
   * @author Patrick Muller
   */
  private double avg(double total) {
    return Math.round(total / sampleCount * 100.0) / 100.0;
  }


  /**
   * Aggregate current payload, emit and reset when window size is reached.
   * @param cpuData New cpu data
   * @param cpuCoreData New cpu core data
   * @param memoryData New memory data
   * @param diskData New disk data
   * @author Patrick Muller
   */
  public void ingest(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData, List<DiskData> diskData) {
    // initialize collections
    if (runningCoreTotals == null) {
      runningCoreTotals = new ArrayList<>(cpuCoreData.size());
      for (int i = 0; i < cpuCoreData.size(); i++)
        runningCoreTotals.add(new CpuCoreData());
    }

    if (runningDiskTotals == null) {
      runningDiskTotals = new ArrayList<>(diskData.size());
      for (int i = 0; i < diskData.size(); i++)
        runningDiskTotals.add(new DiskData());
    }

    // If the number of CPU cores or disks change from a new incoming payload,
    // set the collection to the size of the new payload, reset agg. window
    if (cpuCoreData.size() != runningCoreTotals.size()) {
      runningCoreTotals = new ArrayList<>(cpuCoreData.size());
      for (int i = 0; i < cpuCoreData.size(); i++) {
        runningCoreTotals.add(new CpuCoreData());
      }
      sampleCount = 0;
    }

    if (diskData.size() != runningDiskTotals.size()) {
      runningDiskTotals = new ArrayList<>(diskData.size());
      for (int i = 0; i < diskData.size(); i++) {
        runningDiskTotals.add(new DiskData());
      }
      sampleCount = 0;
    }

    // reset aggregated data after window is complete or size changed
    if (sampleCount == 0) {
      resetState(cpuData, cpuCoreData, memoryData, diskData);
    }
    // reset aggregated data after window is complete
    if (sampleCount == 0)
      resetState(cpuData, cpuCoreData, memoryData, diskData);

    accumulate(cpuData, cpuCoreData, memoryData, diskData);

    sampleCount++;

    // Emit metrics after window is full and reset count
    if (sampleCount >= WINDOW_SIZE) {
      emitAggregatedMetrics();
      sampleCount = 0;
    }

  }
}
