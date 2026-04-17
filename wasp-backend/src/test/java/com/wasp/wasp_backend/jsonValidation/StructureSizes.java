package com.wasp.wasp_backend.jsonValidation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.repository.MetricRepository;
import com.wasp.wasp_backend.service.MetricsAggregationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class StructureSizes {

  private MetricsAggregationService service() {
    return new MetricsAggregationService(mock(MetricRepository.class));
  }

  @Test
  void sameSizedCollectionsShouldWork() {
    MetricsAggregationService service = service();

    assertDoesNotThrow(() ->
      service.ingest(cpu(), cores(4), memory(), disks(2))
    );

    assertDoesNotThrow(() ->
      service.ingest(cpu(), cores(4), memory(), disks(2))
    );
  }

  @Test
  void smallerCpuCoresAfterInitializationShouldThrow() {
    MetricsAggregationService service = service();

    service.ingest(cpu(), cores(4), memory(), disks(2));

    assertThrows(IllegalArgumentException.class, () ->
      service.ingest(cpu(), cores(2), memory(), disks(2))
    );
  }

  @Test
  void largerCpuCoresAfterInitializationShouldThrow() {
    MetricsAggregationService service = service();

    service.ingest(cpu(), cores(2), memory(), disks(1));

    assertThrows(IllegalArgumentException.class, () ->
      service.ingest(cpu(), cores(4), memory(), disks(2))
    );
  }

  @Test
  void smallerDiskAfterInitializationShouldThrow() {
    MetricsAggregationService service = service();

    service.ingest(cpu(), cores(4), memory(), disks(2));

    assertThrows(IllegalArgumentException.class, () ->
      service.ingest(cpu(), cores(4), memory(), disks(1))
    );
  }

  @Test
  void largerDiskAfterInitializationShouldThrow() {
    MetricsAggregationService service = service();

    service.ingest(cpu(), cores(2), memory(), disks(2));

    assertThrows(IllegalArgumentException.class, () ->
      service.ingest(cpu(), cores(4), memory(), disks(4))
    );
  }

  private CpuData cpu() {
    CpuData c = new CpuData();
    c.setCpu_mhz(3000);
    c.setCpu_usage_percent(50);
    c.setSystem_responsiveness_percent(90);
    c.setTimestamp("2026-04-14T10:00:00Z");
    return c;
  }

  private MemoryData memory() {
    MemoryData m = new MemoryData();
    m.setTotal_bytes(16000);
    m.setFree_bytes(8000);
    m.setUsed_bytes(8000);
    m.setMemory_usage_percent(50);
    m.setPage_fault_count(10);
    m.setTimestamp("2026-04-14T10:00:00Z");
    return m;
  }

  private List<CpuCoreData> cores(int count) {
    List<CpuCoreData> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      CpuCoreData c = new CpuCoreData();
      c.setCore_index(i);
      c.setCore_mhz(3000);
      c.setCore_usage_percent(50);
      c.setTimestamp("2026-04-14T10:00:00Z");
      list.add(c);
    }
    return list;
  }

  private List<DiskData> disks(int count) {
    List<DiskData> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      DiskData d = new DiskData();
      d.setDrive_letter("D" + i);
      d.setTotal_bytes(100000);
      d.setFree_bytes(50000);
      d.setRead_speed_bytes_per_sec(1000);
      d.setWrite_speed_bytes_per_sec(500);
      d.setTimestamp("2026-04-14T10:00:00Z");
      list.add(d);
    }
    return list;
  }
}
