package com.wasp.wasp_backend.service;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.dto.ProcessData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class MetricsAggregationServiceTest {

  @Autowired
  private MetricsAggregationService metricsAggregationService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearTables() {
    jdbcTemplate.update("DELETE FROM cpu_cores");
    jdbcTemplate.update("DELETE FROM cpu");
    jdbcTemplate.update("DELETE FROM memory");
    jdbcTemplate.update("DELETE FROM disk");
    jdbcTemplate.update("DELETE FROM processes");
  }

  @Test
  void ingestEmitsAggregatesOnWindowBoundary() {
    for (int i = 0; i < 59; i++) {
      metricsAggregationService.ingest(
        cpu(i),
        cpuCores(i),
        memory(i),
        disk(i),
        processes(i)
      );
    }

    Integer cpuCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
    assertEquals(0, cpuCountBefore);

    metricsAggregationService.ingest(
      cpu(59),
      cpuCores(59),
      memory(59),
      disk(59),
      processes(59)
    );

    Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
    Integer coreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu_cores", Integer.class);
    Integer memoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory", Integer.class);
    Integer diskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM disk", Integer.class);
    Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);

    assertEquals(1, cpuCount);
    assertEquals(2, coreCount);
    assertEquals(1, memoryCount);
    assertEquals(1, diskCount);
    // Process rows are persisted per ingest call.
    assertEquals(60, processCount);
  }

  private CpuData cpu(int sampleIndex) {
    CpuData data = new CpuData();
    data.setCpu_mhz(3200 + sampleIndex);
    data.setCpu_usage_percent(25 + (sampleIndex % 10));
    data.setSystem_responsiveness_percent(90 + (sampleIndex % 5));
    data.setTimestamp(ts(sampleIndex));
    return data;
  }

  private List<CpuCoreData> cpuCores(int sampleIndex) {
    CpuCoreData core0 = new CpuCoreData();
    core0.setCore_index(0);
    core0.setCore_mhz(3100 + sampleIndex);
    core0.setCore_usage_percent(20 + (sampleIndex % 10));
    core0.setTimestamp(ts(sampleIndex));

    CpuCoreData core1 = new CpuCoreData();
    core1.setCore_index(1);
    core1.setCore_mhz(3300 + sampleIndex);
    core1.setCore_usage_percent(30 + (sampleIndex % 10));
    core1.setTimestamp(ts(sampleIndex));

    return List.of(core0, core1);
  }

  private MemoryData memory(int sampleIndex) {
    MemoryData data = new MemoryData();
    data.setTotal_bytes(16_000_000_000L);
    data.setFree_bytes(6_000_000_000L - sampleIndex * 1_000_000L);
    data.setUsed_bytes(10_000_000_000L + sampleIndex * 1_000_000L);
    data.setMemory_usage_percent(62.5 + (sampleIndex % 3));
    data.setPage_fault_count(1000 + sampleIndex);
    data.setTimestamp(ts(sampleIndex));
    return data;
  }

  private List<DiskData> disk(int sampleIndex) {
    DiskData data = new DiskData();
    data.setDrive_letter("C:");
    data.setTotal_bytes(512_000_000_000L);
    data.setFree_bytes(300_000_000_000L - sampleIndex * 100_000_000L);
    data.setRead_speed_bytes_per_sec(1_000_000 + sampleIndex * 100);
    data.setWrite_speed_bytes_per_sec(500_000 + sampleIndex * 100);
    data.setTimestamp(ts(sampleIndex));
    return List.of(data);
  }

  private List<ProcessData> processes(int sampleIndex) {
    ProcessData process = new ProcessData();
    process.setPid(4242 + sampleIndex);
    process.setName("proc-" + sampleIndex + ".exe");
    process.setOwner("user");
    process.setPriority("NORMAL");
    process.setCpu_percent(5.0 + (sampleIndex % 4));
    process.setCpu_time_100ns(100_000L + sampleIndex * 1_000L);
    process.setMem_percent(2.5 + (sampleIndex % 2));
    process.setLocation("C:\\temp\\proc.exe");
    process.setTimestamp(ts(sampleIndex));
    return List.of(process);
  }

  private String ts(int sampleIndex) {
    return String.valueOf(1767225600000L + sampleIndex * 1000L);
  }
}
