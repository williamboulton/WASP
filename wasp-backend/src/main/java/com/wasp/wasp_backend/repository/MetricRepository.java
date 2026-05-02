package com.wasp.wasp_backend.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Repository
public class MetricRepository {
  private static final Logger log = LoggerFactory.getLogger(MetricRepository.class);

  private static final DateTimeFormatter NATIVE_TIMESTAMP_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  // 2 hours in milliseconds
  private static final long RETENTION_WINDOW_MS = 2 * 60 * 60 * 1000L;

  private final JdbcTemplate jdbc;

  public MetricRepository(JdbcTemplate jdbc){
    this.jdbc = jdbc;
  }

  public void health() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS person (id INTEGER, name TEXT)");
    jdbc.update("INSERT INTO person VALUES (?, ?)", 1, "leo");
    jdbc.update("INSERT INTO person VALUES (?, ?)", 2, "yui");

    jdbc.query(
      "SELECT * FROM person",
      rs -> {
        System.out.println("id = " + rs.getInt("id"));
        System.out.println("name = " + rs.getString("name"));
      }
    );
  }

  public void insertCpu(String timestamp, double cpuMhz, double cpuUsagePercent) {
    long timestampMs = parseTimestampMillis(timestamp);
    double cpuGhz = round(cpuMhz / 1000.0);
    int rowsUpdated = jdbc.update(
      "INSERT OR REPLACE INTO cpu (timestamp_ms, cpu_ghz, cpu_usage) VALUES (?, ?, ?)",
      timestampMs,
      cpuGhz,
      round(cpuUsagePercent)
    );
    log.debug("Upserted {} row(s) into cpu for timestamp_ms={}", rowsUpdated, timestampMs);
    pruneTableOlderThan("cpu", timestampMs);
  }

  public void insertCpuCore(String timestamp, int coreIndex, double coreMhz, double coreUsagePercent) {
    long timestampMs = parseTimestampMillis(timestamp);
    double coreGhz = round(coreMhz / 1000.0);
    int rowsUpdated = jdbc.update(
      "INSERT OR REPLACE INTO cpu_cores (timestamp_ms, core_index, core_ghz, core_usage) VALUES (?, ?, ?, ?)",
      timestampMs,
      coreIndex,
      coreGhz,
      round(coreUsagePercent)
    );
    log.debug(
      "Upserted {} row(s) into cpu_cores for timestamp_ms={}, core_index={}",
      rowsUpdated,
      timestampMs,
      coreIndex
    );
    pruneTableOlderThan("cpu_cores", timestampMs);
  }

  public void insertMemory(String timestamp, double totalBytes, double freeBytes, double usedBytes,
                           double memoryUsagePercent, double pageFaults) {
    long timestampMs = parseTimestampMillis(timestamp);
    int rowsUpdated = jdbc.update(
      "INSERT OR REPLACE INTO memory (timestamp_ms, total_mem, free_mem, used_mem, mem_usage, page_faults) VALUES (?, ?, ?, ?, ?, ?)",
      timestampMs,
      Math.round(totalBytes),
      Math.round(freeBytes),
      Math.round(usedBytes),
      round(memoryUsagePercent),
      Math.round(pageFaults)
    );
    log.debug("Upserted {} row(s) into memory for timestamp_ms={}", rowsUpdated, timestampMs);
    pruneTableOlderThan("memory", timestampMs);
  }

  public void insertDisk(String timestamp, String driveLetter, double totalSpace, double freeSpace,
                         double readSpeed, double writeSpeed) {
    long timestampMs = parseTimestampMillis(timestamp);
    int rowsUpdated = jdbc.update(
      "INSERT OR REPLACE INTO disk (timestamp_ms, drive_letter, total_space, free_space, read_speed, write_speed) VALUES (?, ?, ?, ?, ?, ?)",
      timestampMs,
      driveLetter,
      Math.round(totalSpace),
      Math.round(freeSpace),
      round(readSpeed),
      round(writeSpeed)
    );
    log.debug(
      "Upserted {} row(s) into disk for timestamp_ms={}, drive_letter={}",
      rowsUpdated,
      timestampMs,
      driveLetter
    );
    pruneTableOlderThan("disk", timestampMs);
  }

  public void insertProcess(String timestamp, int pid, String name, String owner, String priority,
                            double cpuPercent, long cpuTime100ns, double memPercent, String location) {
    long timestampMs = parseTimestampMillis(timestamp);
    int rowsUpdated = jdbc.update(
      "INSERT OR REPLACE INTO processes (timestamp_ms, pid, name, owner, priority, cpu_percent, cpu_time, mem_percent, location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
      timestampMs,
      pid,
      name,
      (owner == null || owner.isBlank()) ? null : owner,
      parsePriority(priority),
      round(cpuPercent),
      cpuTime100ns,
      round(memPercent),
      (location == null || location.isBlank()) ? null : location
    );
    log.debug(
      "Upserted {} row(s) into processes for timestamp_ms={}, pid={}",
      rowsUpdated,
      timestampMs,
      pid
    );
    pruneTableOlderThan("processes", timestampMs);
  }

  private long parseTimestampMillis(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      throw new IllegalArgumentException("timestamp must not be null or blank");
    }

    String normalized = timestamp.trim();
    try {
      long value = Long.parseLong(normalized);
      // Treat 10-digit values as epoch seconds
      return normalized.length() <= 10 ? value * 1000 : value;
    } catch (NumberFormatException ignored) {
      // Try parsing as an ISO instant first, then local date-time.
    }

    try {
      return Instant.parse(normalized).toEpochMilli();
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli();
      } catch (DateTimeParseException e) {
        try {
          return LocalDateTime.parse(normalized, NATIVE_TIMESTAMP_FORMAT)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        } catch (DateTimeParseException e2) {
          throw new IllegalArgumentException("Unable to parse timestamp: " + timestamp, e2);
        }
      }
    }
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private Integer parsePriority(String priority) {
    if (priority == null || priority.isBlank()) {
      return null;
    }

    String normalized = priority.trim().toUpperCase();
    try {
      return Integer.parseInt(normalized);
    } catch (NumberFormatException ignored) {
      return switch (normalized) {
        case "IDLE" -> 4;
        case "BELOW_NORMAL" -> 6;
        case "NORMAL" -> 8;
        case "ABOVE_NORMAL" -> 10;
        case "HIGH" -> 13;
        case "REALTIME" -> 24;
        default -> null;
      };
    }
  }

  private void pruneTableOlderThan(String tableName, long referenceTimestampMs) {
    long cutoffTimestampMs = referenceTimestampMs - RETENTION_WINDOW_MS;
    if (cutoffTimestampMs <= 0) {
      return;
    }
    jdbc.update("DELETE FROM " + tableName + " WHERE timestamp_ms < ?", cutoffTimestampMs);
  }
}
