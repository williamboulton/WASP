package com.wasp.wasp_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ActiveProfiles("test")
@SpringBootTest
class MetricRepositoryTest {

  @Autowired
  private MetricRepository metricRepository;

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
  void insertCpuWritesAggregatedValues() {
    metricRepository.insertCpu("1735689600000", 3600.0, 78.345);

    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM cpu WHERE timestamp_ms = ?",
      Integer.class,
      1735689600000L
    );
    Double ghz = jdbcTemplate.queryForObject(
      "SELECT cpu_ghz FROM cpu WHERE timestamp_ms = ?",
      Double.class,
      1735689600000L
    );
    Double usage = jdbcTemplate.queryForObject(
      "SELECT cpu_usage FROM cpu WHERE timestamp_ms = ?",
      Double.class,
      1735689600000L
    );

    assertEquals(1, count);
    assertEquals(3.6, ghz);
    assertEquals(78.35, usage);
  }

  @Test
  void insertCpuCoreWritesRowsWithCompositeKey() {
    metricRepository.insertCpuCore("1735689600", 2, 4200.0, 67.891);

    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
      Integer.class,
      1735689600000L,
      2
    );
    Double coreGhz = jdbcTemplate.queryForObject(
      "SELECT core_ghz FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
      Double.class,
      1735689600000L,
      2
    );
    Double coreUsage = jdbcTemplate.queryForObject(
      "SELECT core_usage FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
      Double.class,
      1735689600000L,
      2
    );

    assertEquals(1, count);
    assertEquals(4.2, coreGhz);
    assertEquals(67.89, coreUsage);
  }

  @Test
  void insertMemoryWritesByteAndPercentColumns() {
    metricRepository.insertMemory(
      "2026-01-01T00:00:00Z",
      1000.4,
      250.6,
      749.8,
      74.997,
      13.6
    );

    Long timestampMs = 1767225600000L;
    Long totalMem = jdbcTemplate.queryForObject(
      "SELECT total_mem FROM memory WHERE timestamp_ms = ?",
      Long.class,
      timestampMs
    );
    Long freeMem = jdbcTemplate.queryForObject(
      "SELECT free_mem FROM memory WHERE timestamp_ms = ?",
      Long.class,
      timestampMs
    );
    Long usedMem = jdbcTemplate.queryForObject(
      "SELECT used_mem FROM memory WHERE timestamp_ms = ?",
      Long.class,
      timestampMs
    );
    Double memUsage = jdbcTemplate.queryForObject(
      "SELECT mem_usage FROM memory WHERE timestamp_ms = ?",
      Double.class,
      timestampMs
    );
    Long pageFaults = jdbcTemplate.queryForObject(
      "SELECT page_faults FROM memory WHERE timestamp_ms = ?",
      Long.class,
      timestampMs
    );

    assertEquals(1000L, totalMem);
    assertEquals(251L, freeMem);
    assertEquals(750L, usedMem);
    assertEquals(75.0, memUsage);
    assertEquals(14L, pageFaults);
  }

  @Test
  void insertDiskWritesPerDriveRows() {
    metricRepository.insertDisk(
      "2026-01-01T00:00:00Z",
      "C:",
      2_000_000.4,
      500_000.4,
      1024.567,
      768.432
    );

    Long timestampMs = 1767225600000L;
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
      Integer.class,
      timestampMs,
      "C:"
    );
    Long totalSpace = jdbcTemplate.queryForObject(
      "SELECT total_space FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
      Long.class,
      timestampMs,
      "C:"
    );
    Long freeSpace = jdbcTemplate.queryForObject(
      "SELECT free_space FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
      Long.class,
      timestampMs,
      "C:"
    );
    Double readSpeed = jdbcTemplate.queryForObject(
      "SELECT read_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
      Double.class,
      timestampMs,
      "C:"
    );
    Double writeSpeed = jdbcTemplate.queryForObject(
      "SELECT write_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
      Double.class,
      timestampMs,
      "C:"
    );

    assertEquals(1, count);
    assertEquals(2_000_000L, totalSpace);
    assertEquals(500_000L, freeSpace);
    assertEquals(1024.57, readSpeed);
    assertEquals(768.43, writeSpeed);
  }

  @Test
  void insertProcessWritesExpectedColumns() {
    metricRepository.insertProcess(
      "2026-01-01 00:00:00.000",
      4242,
      "example.exe",
      "",
      "NORMAL",
      12.345,
      500000L,
      8.765,
      ""
    );

    Long timestampMs = 1767225600000L;
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM processes WHERE timestamp_ms = ? AND pid = ?",
      Integer.class,
      timestampMs,
      4242
    );
    Integer priority = jdbcTemplate.queryForObject(
      "SELECT priority FROM processes WHERE timestamp_ms = ? AND pid = ?",
      Integer.class,
      timestampMs,
      4242
    );
    Double cpuPercent = jdbcTemplate.queryForObject(
      "SELECT cpu_percent FROM processes WHERE timestamp_ms = ? AND pid = ?",
      Double.class,
      timestampMs,
      4242
    );
    Long cpuTime = jdbcTemplate.queryForObject(
      "SELECT cpu_time FROM processes WHERE timestamp_ms = ? AND pid = ?",
      Long.class,
      timestampMs,
      4242
    );
    Double memPercent = jdbcTemplate.queryForObject(
      "SELECT mem_percent FROM processes WHERE timestamp_ms = ? AND pid = ?",
      Double.class,
      timestampMs,
      4242
    );
    String owner = jdbcTemplate.queryForObject(
      "SELECT owner FROM processes WHERE timestamp_ms = ? AND pid = ?",
      String.class,
      timestampMs,
      4242
    );
    String location = jdbcTemplate.queryForObject(
      "SELECT location FROM processes WHERE timestamp_ms = ? AND pid = ?",
      String.class,
      timestampMs,
      4242
    );

    assertEquals(1, count);
    assertEquals(8, priority);
    assertEquals(12.35, cpuPercent);
    assertEquals(500000L, cpuTime);
    assertEquals(8.77, memPercent);
    assertNull(owner);
    assertNull(location);
  }

  @Test
  void insertCpuReplacesExistingRowWhenPrimaryKeyMatches() {
    long timestampMs = 1767225600000L;

    metricRepository.insertCpu("2026-01-01T00:00:00Z", 3200.0, 15.0);
    metricRepository.insertCpu("2026-01-01T00:00:00Z", 4100.0, 63.456);

    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM cpu WHERE timestamp_ms = ?",
      Integer.class,
      timestampMs
    );
    Double ghz = jdbcTemplate.queryForObject(
      "SELECT cpu_ghz FROM cpu WHERE timestamp_ms = ?",
      Double.class,
      timestampMs
    );
    Double usage = jdbcTemplate.queryForObject(
      "SELECT cpu_usage FROM cpu WHERE timestamp_ms = ?",
      Double.class,
      timestampMs
    );

    assertEquals(1, count);
    assertEquals(4.1, ghz);
    assertEquals(63.46, usage);
  }
}
