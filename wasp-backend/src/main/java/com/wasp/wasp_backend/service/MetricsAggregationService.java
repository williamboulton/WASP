package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.dto.ProcessData;
import com.wasp.wasp_backend.event.BackendNotificationEvent;
import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

  private final int metricsWindowSize;
 
  private final int processWindowSize;

  private int metricsSampleCount = 0;
  private int processSampleCount = 0;

  private final CpuData runningCpuTotals = new CpuData();

  private List<CpuCoreData> runningCoreTotals;

  private final MemoryData runningMemTotals = new MemoryData();

  private List<DiskData> runningDiskTotals;

  private List<ProcessData> latestProcessSnapshot = List.of();

  private final MetricRepository metricRepository;
  private final ApplicationEventPublisher eventPublisher;

  public MetricsAggregationService(MetricRepository metricRepository, ApplicationEventPublisher eventPublisher) {
    this(metricRepository, eventPublisher, 60, 1);
  }

  @Autowired
  public MetricsAggregationService(
    MetricRepository metricRepository,
    ApplicationEventPublisher eventPublisher,
    @Value("${wasp.aggregation.metrics-window-size:60}") int metricsWindowSize,
    @Value("${wasp.aggregation.process-window-size:1}") int processWindowSize
  ) {
    this.metricRepository = metricRepository;
    this.eventPublisher = eventPublisher;
    this.metricsWindowSize = Math.max(1, metricsWindowSize);
    this.processWindowSize = Math.max(1, processWindowSize);
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
  private WriteStats emitAggregatedMetrics() {
    int upsertedRows = 0;
    int prunedRows = 0;

    double aggCpuMhz = avg(runningCpuTotals.getCpu_mhz());
    double aggCpuUsage = avg(runningCpuTotals.getCpu_usage_percent());
    double aggCpuSystemResp = avg(runningCpuTotals.getSystem_responsiveness_percent());

    // Persist CPU aggregate to DB
    var cpuSummary = metricRepository.insertCpu(runningCpuTotals.getTimestamp(), aggCpuMhz, aggCpuUsage);
    upsertedRows += cpuSummary.upsertedRows();
    prunedRows += cpuSummary.prunedRows();

    Map<String, Object> root = new HashMap<>();
    Map<String, Object> cpu = new HashMap<>();
    cpu.put("cpu_mhz", aggCpuMhz);
    cpu.put("cpu_usage_percent", aggCpuUsage);
    cpu.put("system_responsiveness_percent", aggCpuSystemResp);
    cpu.put("timestamp", runningCpuTotals.getTimestamp());
    root.put("cpu", cpu);

    List<Map<String, Object>> cores = new ArrayList<>();
    for (CpuCoreData core : runningCoreTotals) {
      double aggCpuCoreMhz = avg(core.getCore_mhz());
      double aggCpuCoreUsage = avg(core.getCore_usage_percent());

      // Persist per-core aggregate to DB
      var cpuCoreSummary = metricRepository.insertCpuCore(
        core.getTimestamp(),
        core.getCore_index(),
        aggCpuCoreMhz,
        aggCpuCoreUsage
      );
      upsertedRows += cpuCoreSummary.upsertedRows();
      prunedRows += cpuCoreSummary.prunedRows();

      Map<String, Object> coreMap = new HashMap<>();
      coreMap.put("core_index", core.getCore_index());
      coreMap.put("core_mhz", aggCpuCoreMhz);
      coreMap.put("core_usage_percent", aggCpuCoreUsage);
      coreMap.put("timestamp", core.getTimestamp());
      cores.add(coreMap);
    }

    root.put("cpu_cores", cores);
    double aggMemTotalBytes = avg((double) runningMemTotals.getTotal_bytes());
    double aggMemFreeBytes = avg((double) runningMemTotals.getFree_bytes());
    double aggMemUsedBytes = avg((double) runningMemTotals.getUsed_bytes());
    double aggMemUsagePercent = avg(runningMemTotals.getMemory_usage_percent());
    double aggMemPageFaultCount = avg((double) runningMemTotals.getPage_fault_count());

    // Persist memory aggregate to DB
    var memorySummary = metricRepository.insertMemory(
      runningMemTotals.getTimestamp(),
      aggMemTotalBytes,
      aggMemFreeBytes,
      aggMemUsedBytes,
      aggMemUsagePercent,
      aggMemPageFaultCount
    );
    upsertedRows += memorySummary.upsertedRows();
    prunedRows += memorySummary.prunedRows();

    Map<String, Object> memory = new HashMap<>();
    memory.put("total_bytes", aggMemTotalBytes);
    memory.put("free_bytes", aggMemFreeBytes);
    memory.put("used_bytes", aggMemUsedBytes);
    memory.put("memory_usage_percent", aggMemUsagePercent);
    memory.put("page_fault_count", aggMemPageFaultCount);
    memory.put("timestamp", runningMemTotals.getTimestamp());
    root.put("memory", memory);

    List<Map<String, Object>> disks = new ArrayList<>();
    for (DiskData disk : runningDiskTotals) {
      double aggDiskTotalBytes = avg((double) disk.getTotal_bytes());
      double aggDiskFreeBytes = avg((double) disk.getFree_bytes());
      double aggDiskReadSpeed = avg(disk.getRead_speed_bytes_per_sec());
      double aggDiskWriteSpeed = avg(disk.getWrite_speed_bytes_per_sec());

      // Persist disk aggregate to DB
      var diskSummary = metricRepository.insertDisk(
        disk.getTimestamp(),
        disk.getDrive_letter(),
        aggDiskTotalBytes,
        aggDiskFreeBytes,
        aggDiskReadSpeed,
        aggDiskWriteSpeed
      );
      upsertedRows += diskSummary.upsertedRows();
      prunedRows += diskSummary.prunedRows();

      Map<String, Object> diskMap = new HashMap<>();
      diskMap.put("drive", disk.getDrive_letter());
      diskMap.put("total_bytes", aggDiskTotalBytes);
      diskMap.put("free_bytes", aggDiskFreeBytes);
      diskMap.put("read_speed", aggDiskReadSpeed);
      diskMap.put("write_speed", aggDiskWriteSpeed);
      diskMap.put("timestamp", disk.getTimestamp());
      disks.add(diskMap);
    }

    root.put("disks", disks);

    // Write JSON file
    try {
      JsonMapper mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

      File outputFile = new File("output/", "output.json");
      outputFile.getParentFile().mkdirs();
      mapper.writeValue(outputFile, root);

    } catch (Exception e) {
      e.printStackTrace();
    }

    return new WriteStats(upsertedRows, prunedRows);
  }

  /**
   * Average out accumulated metrics by the sample count and round to nearest hundredth
   *
   * @param total Accumulated metrics
   * @return Averaged value
   * @author Patrick Muller
   */
  private double avg(double total) {
    return Math.round(total / metricsSampleCount * 100.0) / 100.0;
  }

  private WriteStats persistProcesses(List<ProcessData> processData) {
    int upsertedRows = 0;
    int prunedRows = 0;
    for (ProcessData process : processData) {
      var processSummary = metricRepository.insertProcess(
        process.getTimestamp(),
        process.getPid(),
        process.getName(),
        process.getOwner(),
        process.getPriority(),
        process.getCpu_percent(),
        process.getCpu_time_100ns(),
        process.getMem_percent(),
        process.getLocation()
      );
      upsertedRows += processSummary.upsertedRows();
      prunedRows += processSummary.prunedRows();
    }

    return new WriteStats(upsertedRows, prunedRows);
  }

  private void emitDatabaseNotification(String title, WriteStats stats) {
    if (stats.upsertedRows() <= 0 && stats.prunedRows() <= 0) {
      return;
    }

    StringBuilder messageBuilder = new StringBuilder();
    if (stats.upsertedRows() > 0) {
      messageBuilder.append("Updated ")
        .append(stats.upsertedRows())
        .append(" row(s)");
    }
    if (stats.prunedRows() > 0) {
      if (messageBuilder.length() > 0) {
        messageBuilder.append(", ");
      }
      messageBuilder.append("pruned ")
        .append(stats.prunedRows())
        .append(" row(s)");
    }

    eventPublisher.publishEvent(
      new BackendNotificationEvent("info", "database", title, messageBuilder.toString())
    );
  }


  /**
   * Aggregate current payload, emit and reset when window size is reached.
   * @param cpuData New cpu data
   * @param cpuCoreData New cpu core data
   * @param memoryData New memory data
   * @param diskData New disk data
   * @param processData New process data
   * @author Patrick Muller
   */
  public void ingest(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData,
                     List<DiskData> diskData, List<ProcessData> processData) {
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
      metricsSampleCount = 0;
    }

    if (diskData.size() != runningDiskTotals.size()) {
      runningDiskTotals = new ArrayList<>(diskData.size());
      for (int i = 0; i < diskData.size(); i++) {
        runningDiskTotals.add(new DiskData());
      }
      metricsSampleCount = 0;
    }

    // reset aggregated data after window is complete or size changed
    if (metricsSampleCount == 0) {
      resetState(cpuData, cpuCoreData, memoryData, diskData);
    }

    accumulate(cpuData, cpuCoreData, memoryData, diskData);
    latestProcessSnapshot = new ArrayList<>(processData);

    metricsSampleCount++;
    processSampleCount++;

    // Emit metrics after window is full and reset count
    if (metricsSampleCount >= metricsWindowSize) {
      WriteStats aggregateStats = emitAggregatedMetrics();
      emitDatabaseNotification("Database Update", aggregateStats);
      metricsSampleCount = 0;
    }

    // Persist latest process snapshot on a slower cadence.
    if (processSampleCount >= processWindowSize) {
      WriteStats processStats = persistProcesses(latestProcessSnapshot);
      emitDatabaseNotification("Process Snapshot Saved", processStats);
      processSampleCount = 0;
    }

  }

  /**
   * Backwards-compatible overload for callers that do not provide processes.
   */
  public void ingest(CpuData cpuData, List<CpuCoreData> cpuCoreData, MemoryData memoryData,
                     List<DiskData> diskData) {
    ingest(cpuData, cpuCoreData, memoryData, diskData, List.of());
  }

  private record WriteStats(int upsertedRows, int prunedRows) {
  }
}
