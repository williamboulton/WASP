package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.dto.ProcessData;
import com.wasp.wasp_backend.repository.MetricRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    double aggCpuMhz = avg(runningCpuTotals.getCpu_mhz());
    double aggCpuUsage = avg(runningCpuTotals.getCpu_usage_percent());
    double aggCpuSystemResp = avg(runningCpuTotals.getSystem_responsiveness_percent());

    metricRepository.insertCpu(runningCpuTotals.getTimestamp(), aggCpuMhz, aggCpuUsage);

    System.out.println("Aggregated CPU Mhz: " + aggCpuMhz);
    System.out.println("Aggregated Cpu Usage %: " + aggCpuUsage);
    System.out.println("Aggregated Cpu System Responsiveness %: " + aggCpuSystemResp);

    for (int i = 0; i < runningCoreTotals.size(); i++) {
      CpuCoreData currAggCore = runningCoreTotals.get(i);
      double aggCpuCoreMhz = avg(currAggCore.getCore_mhz());
      double aggCpuCoreUsage = avg(currAggCore.getCore_usage_percent());
      metricRepository.insertCpuCore(
        currAggCore.getTimestamp(),
        currAggCore.getCore_index(),
        aggCpuCoreMhz,
        aggCpuCoreUsage
      );
      System.out.println("Aggregated Cpu Core: {" + currAggCore.getCore_index() + "} Mhz: " + aggCpuCoreMhz);
      System.out.println("Aggregated Cpu Core: {" + currAggCore.getCore_index() + "} Core Usage: " + aggCpuCoreUsage);
    }

    // Cast long's to doubles for floating point division, not integer division
    double aggMemTotalBytes = avg((double) runningMemTotals.getTotal_bytes());
    double aggMemFreeBytes = avg((double) runningMemTotals.getFree_bytes());
    double aggMemUsedBytes = avg((double) runningMemTotals.getUsed_bytes());
    double aggMemUsagePercent = avg(runningMemTotals.getMemory_usage_percent());
    double aggMemPageFaultCount = avg((double) runningMemTotals.getPage_fault_count());

    metricRepository.insertMemory(
      runningMemTotals.getTimestamp(),
      aggMemTotalBytes,
      aggMemFreeBytes,
      aggMemUsedBytes,
      aggMemUsagePercent,
      aggMemPageFaultCount
    );

    System.out.println("Aggregated Mem Total Bytes: " + aggMemTotalBytes);
    System.out.println("Aggregated Mem Free Bytes: " + aggMemFreeBytes);
    System.out.println("Aggregated Mem Used Bytes: " + aggMemUsedBytes);
    System.out.println("Aggregated Mem Usage Percent: " + aggMemUsagePercent);
    System.out.println("Aggregated Mem Page Fault Count: " + aggMemPageFaultCount);

    for (int i = 0; i < runningDiskTotals.size(); i++) {
      DiskData currAggDisk = this.runningDiskTotals.get(i);
      double aggDiskTotalBytes = avg((double) currAggDisk.getTotal_bytes());
      double aggDiskFreeBytes = avg((double) currAggDisk.getFree_bytes());
      double aggDiskReadSpeed = avg(currAggDisk.getRead_speed_bytes_per_sec());
      double aggDiskWriteSpeed = avg(currAggDisk.getWrite_speed_bytes_per_sec());
      metricRepository.insertDisk(
        currAggDisk.getTimestamp(),
        currAggDisk.getDrive_letter(),
        aggDiskTotalBytes,
        aggDiskFreeBytes,
        aggDiskReadSpeed,
        aggDiskWriteSpeed
      );
      System.out.println("Aggregated Disk: {" + currAggDisk.getDrive_letter() + "} Total Bytes: " + aggDiskTotalBytes);
      System.out.println("Aggregated Disk: {" + currAggDisk.getDrive_letter() + "} Free Bytes: " + aggDiskFreeBytes);
      System.out.println("Aggregated Disk: {" + currAggDisk.getDrive_letter() + "} Read Speed: " + aggDiskReadSpeed);
      System.out.println("Aggregated Disk: {" + currAggDisk.getDrive_letter() + "} Write Speed: " + aggDiskWriteSpeed);
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

  private void persistProcesses(List<ProcessData> processData) {
    for (ProcessData process : processData) {
      metricRepository.insertProcess(
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
    }
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

    // reset aggregated data after window is complete
    if (sampleCount == 0)
      resetState(cpuData, cpuCoreData, memoryData, diskData);

    accumulate(cpuData, cpuCoreData, memoryData, diskData);
    persistProcesses(processData);

    sampleCount++;

    // Emit metrics after window is full and reset count
    if (sampleCount >= WINDOW_SIZE) {
      emitAggregatedMetrics();
      sampleCount = 0;
    }

  }
}
