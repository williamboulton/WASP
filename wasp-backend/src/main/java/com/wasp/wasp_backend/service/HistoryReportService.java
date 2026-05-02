package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.HistoryData;
import com.wasp.wasp_backend.repository.HistoryRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.List;

@Service
public class HistoryReportService {

  HistoryRepository historyRepository;

  public HistoryReportService(HistoryRepository historyRepository) {
    this.historyRepository = historyRepository;
  }

  public byte[] generateHistoryCsv() {
    long fiveMinutes = System.currentTimeMillis() - (5 * 60 * 1000);
    long thirtyMinutes = System.currentTimeMillis() - (30 * 60 * 1000);
    long oneHour = System.currentTimeMillis() - (60 * 60 * 1000);

    HistoryData fiveMinHistoryData = historyRepository.getHistoryData(fiveMinutes);
    HistoryData thirtyMinHistoryData = historyRepository.getHistoryData(thirtyMinutes);
    HistoryData oneHourHistoryData = historyRepository.getHistoryData(oneHour);

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try (PrintWriter writer = new PrintWriter(out)) {

      writer.println(String.join(",",
        "avg_cpu_ghz",
        "avg_cpu_usage",
        "avg_core_ghz",
        "avg_core_usage",
        "avg_total_mem",
        "avg_free_mem",
        "avg_used_mem",
        "avg_mem_usage",
        "avg_page_faults",
        "avg_total_disk_space",
        "avg_free_disk_space",
        "avg_read_speed",
        "avg_write_speed"
      ));

      writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
        fiveMinHistoryData.getAvgCpuGhz(),
        fiveMinHistoryData.getAvgCpuUsage(),
        fiveMinHistoryData.getAvgCoreGhz(),
        fiveMinHistoryData.getAvgCoreUsage(),
        fiveMinHistoryData.getAvgTotalMem(),
        fiveMinHistoryData.getAvgFreeMem(),
        fiveMinHistoryData.getAvgUsedMem(),
        fiveMinHistoryData.getAvgMemUsage(),
        fiveMinHistoryData.getAvgPageFaults(),
        fiveMinHistoryData.getAvgTotalDiskSpace(),
        fiveMinHistoryData.getAvgFreeDiskSpace(),
        fiveMinHistoryData.getAvgReadSpeed(),
        fiveMinHistoryData.getAvgWriteSpeed()
      );

      writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
        thirtyMinHistoryData.getAvgCpuGhz(),
        thirtyMinHistoryData.getAvgCpuUsage(),
        thirtyMinHistoryData.getAvgCoreGhz(),
        thirtyMinHistoryData.getAvgCoreUsage(),
        thirtyMinHistoryData.getAvgTotalMem(),
        thirtyMinHistoryData.getAvgFreeMem(),
        thirtyMinHistoryData.getAvgUsedMem(),
        thirtyMinHistoryData.getAvgMemUsage(),
        thirtyMinHistoryData.getAvgPageFaults(),
        thirtyMinHistoryData.getAvgTotalDiskSpace(),
        thirtyMinHistoryData.getAvgFreeDiskSpace(),
        thirtyMinHistoryData.getAvgReadSpeed(),
        thirtyMinHistoryData.getAvgWriteSpeed()
      );

      writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
        oneHourHistoryData.getAvgCpuGhz(),
        oneHourHistoryData.getAvgCpuUsage(),
        oneHourHistoryData.getAvgCoreGhz(),
        oneHourHistoryData.getAvgCoreUsage(),
        oneHourHistoryData.getAvgTotalMem(),
        oneHourHistoryData.getAvgFreeMem(),
        oneHourHistoryData.getAvgUsedMem(),
        oneHourHistoryData.getAvgMemUsage(),
        oneHourHistoryData.getAvgPageFaults(),
        oneHourHistoryData.getAvgTotalDiskSpace(),
        oneHourHistoryData.getAvgFreeDiskSpace(),
        oneHourHistoryData.getAvgReadSpeed(),
        oneHourHistoryData.getAvgWriteSpeed()
      );
    }

    return out.toByteArray();
  }
}
