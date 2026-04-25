package com.wasp.wasp_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class MetricsWebSocketBlackBoxTestSupport {

  @LocalServerPort
  protected int port;

  @Autowired
  protected JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearTables() {
    jdbcTemplate.update("DELETE FROM cpu_cores");
    jdbcTemplate.update("DELETE FROM cpu");
    jdbcTemplate.update("DELETE FROM memory");
    jdbcTemplate.update("DELETE FROM disk");
    jdbcTemplate.update("DELETE FROM processes");
  }

  protected WebSocket openSocket(TestWebSocketListener listener) throws Exception {
    return HttpClient.newHttpClient()
      .newWebSocketBuilder()
      .buildAsync(URI.create("ws://localhost:" + port + "/ws/metrics"), listener)
      .get(5, TimeUnit.SECONDS);
  }

  protected boolean waitForCpuAggregate(long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
      if (cpuCount != null && cpuCount > 0) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  protected boolean waitForCpuAggregateCount(int expectedCount, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
      if (cpuCount != null && cpuCount >= expectedCount) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  protected boolean waitForProcessCount(int expectedCount, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);
      if (processCount != null && processCount >= expectedCount) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  protected boolean waitForDiskCount(int expectedCount, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      Integer diskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM disk", Integer.class);
      if (diskCount != null && diskCount >= expectedCount) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  protected void sendSamples(WebSocket webSocket, int startIndexInclusive, int count) {
    int endExclusive = startIndexInclusive + count;
    for (int i = startIndexInclusive; i < endExclusive; i++) {
      webSocket.sendText(payload(i), true).join();
    }
  }

  protected boolean waitForMessageContains(TestWebSocketListener listener, String needle, long timeoutMs)
    throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (String message : listener.messages) {
        if (message.contains(needle)) {
          return true;
        }
      }
      Thread.sleep(50);
    }
    return false;
  }

  protected void assertNoRowsPersisted() {
    Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
    Integer coreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu_cores", Integer.class);
    Integer memoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory", Integer.class);
    Integer diskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM disk", Integer.class);
    Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);

    assertEquals(0, cpuCount);
    assertEquals(0, coreCount);
    assertEquals(0, memoryCount);
    assertEquals(0, diskCount);
    assertEquals(0, processCount);
  }

  protected String payloadMissingCpuTimestamp() {
    return """
      {
        "cpu": {
          "cpu_mhz": 3200,
          "cpu_usage_percent": 30,
          "system_responsiveness_percent": 95
        },
        "cpu_cores": [
          {
            "core_index": 0,
            "core_mhz": 3100,
            "core_usage_percent": 25,
            "timestamp": "1767225600000"
          }
        ],
        "memory": {
          "total_bytes": 16000000000,
          "free_bytes": 6000000000,
          "used_bytes": 10000000000,
          "memory_usage_percent": 62.5,
          "page_fault_count": 1000,
          "timestamp": "1767225600000"
        },
        "disk": [
          {
            "drive_letter": "C:",
            "total_bytes": 512000000000,
            "free_bytes": 300000000000,
            "read_speed_bytes_per_sec": 1000000,
            "write_speed_bytes_per_sec": 500000,
            "timestamp": "1767225600000"
          }
        ],
        "processes": [
          {
            "pid": 4242,
            "name": "proc-0.exe",
            "owner": "",
            "priority": "NORMAL",
            "cpu_percent": 5.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.5,
            "location": "",
            "timestamp": "1767225600000"
          }
        ]
      }
      """;
  }

  protected String payloadWithInvalidProcessTimestamp() {
    return """
      {
        "cpu": {
          "cpu_mhz": 3200,
          "cpu_usage_percent": 30,
          "system_responsiveness_percent": 95,
          "timestamp": "1767225600000"
        },
        "cpu_cores": [
          {
            "core_index": 0,
            "core_mhz": 3100,
            "core_usage_percent": 25,
            "timestamp": "1767225600000"
          }
        ],
        "memory": {
          "total_bytes": 16000000000,
          "free_bytes": 6000000000,
          "used_bytes": 10000000000,
          "memory_usage_percent": 62.5,
          "page_fault_count": 1000,
          "timestamp": "1767225600000"
        },
        "disk": [
          {
            "drive_letter": "C:",
            "total_bytes": 512000000000,
            "free_bytes": 300000000000,
            "read_speed_bytes_per_sec": 1000000,
            "write_speed_bytes_per_sec": 500000,
            "timestamp": "1767225600000"
          }
        ],
        "processes": [
          {
            "pid": 9999,
            "name": "bad-ts.exe",
            "owner": "",
            "priority": "NORMAL",
            "cpu_percent": 3.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.0,
            "location": "",
            "timestamp": "not-a-valid-timestamp"
          }
        ]
      }
      """;
  }

  protected String payloadWithTwoDisks(int sampleIndex) {
    long timestampMs = 1767225600000L + sampleIndex * 1000L;
    return String.format(
      """
        {
          "cpu": {
            "cpu_mhz": %d,
            "cpu_usage_percent": %d,
            "system_responsiveness_percent": %d,
            "timestamp": "%d"
          },
          "cpu_cores": [
            {
              "core_index": 0,
              "core_mhz": %d,
              "core_usage_percent": %d,
              "timestamp": "%d"
            },
            {
              "core_index": 1,
              "core_mhz": %d,
              "core_usage_percent": %d,
              "timestamp": "%d"
            }
          ],
          "memory": {
            "total_bytes": 16000000000,
            "free_bytes": %d,
            "used_bytes": %d,
            "memory_usage_percent": %.1f,
            "page_fault_count": %d,
            "timestamp": "%d"
          },
          "disk": [
            {
              "drive_letter": "C:",
              "total_bytes": 512000000000,
              "free_bytes": %d,
              "read_speed_bytes_per_sec": %d,
              "write_speed_bytes_per_sec": %d,
              "timestamp": "%d"
            },
            {
              "drive_letter": "D:",
              "total_bytes": 1024000000000,
              "free_bytes": %d,
              "read_speed_bytes_per_sec": %d,
              "write_speed_bytes_per_sec": %d,
              "timestamp": "%d"
            }
          ],
          "processes": [
            {
              "pid": %d,
              "name": "proc-%d.exe",
              "owner": "",
              "priority": "NORMAL",
              "cpu_percent": %.1f,
              "cpu_time_100ns": %d,
              "mem_percent": %.1f,
              "location": "",
              "timestamp": "%d"
            }
          ]
        }
        """,
      3200 + sampleIndex,
      25 + (sampleIndex % 10),
      90 + (sampleIndex % 5),
      timestampMs,
      3100 + sampleIndex,
      20 + (sampleIndex % 10),
      timestampMs,
      3300 + sampleIndex,
      30 + (sampleIndex % 10),
      timestampMs,
      6_000_000_000L - sampleIndex * 1_000_000L,
      10_000_000_000L + sampleIndex * 1_000_000L,
      62.5 + (sampleIndex % 3),
      1000 + sampleIndex,
      timestampMs,
      300_000_000_000L - sampleIndex * 100_000_000L,
      1_000_000 + sampleIndex * 100,
      500_000 + sampleIndex * 100,
      timestampMs,
      700_000_000_000L - sampleIndex * 50_000_000L,
      2_000_000 + sampleIndex * 50,
      1_250_000 + sampleIndex * 50,
      timestampMs,
      4242 + sampleIndex,
      sampleIndex,
      5.0 + (sampleIndex % 4),
      100_000L + sampleIndex * 1_000L,
      2.5 + (sampleIndex % 2),
      timestampMs
    );
  }

  protected String payloadWithPriorityMatrix() {
    return """
      {
        "cpu": {
          "cpu_mhz": 3200,
          "cpu_usage_percent": 30,
          "system_responsiveness_percent": 95,
          "timestamp": "1767225600000"
        },
        "cpu_cores": [
          {
            "core_index": 0,
            "core_mhz": 3100,
            "core_usage_percent": 25,
            "timestamp": "1767225600000"
          }
        ],
        "memory": {
          "total_bytes": 16000000000,
          "free_bytes": 6000000000,
          "used_bytes": 10000000000,
          "memory_usage_percent": 62.5,
          "page_fault_count": 1000,
          "timestamp": "1767225600000"
        },
        "disk": [
          {
            "drive_letter": "C:",
            "total_bytes": 512000000000,
            "free_bytes": 300000000000,
            "read_speed_bytes_per_sec": 1000000,
            "write_speed_bytes_per_sec": 500000,
            "timestamp": "1767225600000"
          }
        ],
        "processes": [
          {
            "pid": 8001,
            "name": "high.exe",
            "owner": "",
            "priority": "HIGH",
            "cpu_percent": 5.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.5,
            "location": "",
            "timestamp": "1767225600000"
          },
          {
            "pid": 8002,
            "name": "realtime.exe",
            "owner": "",
            "priority": "REALTIME",
            "cpu_percent": 5.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.5,
            "location": "",
            "timestamp": "1767225600000"
          },
          {
            "pid": 8003,
            "name": "numeric.exe",
            "owner": "",
            "priority": "10",
            "cpu_percent": 5.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.5,
            "location": "",
            "timestamp": "1767225600000"
          },
          {
            "pid": 8004,
            "name": "unknown.exe",
            "owner": "",
            "priority": "NOT_A_PRIORITY",
            "cpu_percent": 5.0,
            "cpu_time_100ns": 100000,
            "mem_percent": 2.5,
            "location": "",
            "timestamp": "1767225600000"
          }
        ]
      }
      """;
  }

  protected String payloadWithTimestampFormatVariants() {
    String processesJson = """
      {
        "pid": 9001,
        "name": "format-seconds.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": 5.0,
        "cpu_time_100ns": 100000,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "1735689600"
      },
      {
        "pid": 9001,
        "name": "format-millis.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": 6.0,
        "cpu_time_100ns": 200000,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "1735689600000"
      },
      {
        "pid": 9001,
        "name": "format-iso.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": 7.0,
        "cpu_time_100ns": 300000,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "2025-01-01T00:00:00Z"
      }
      """;
    return payloadWithProcessArray(processesJson);
  }

  protected String payloadWithSingleProcess(String timestamp, int pid, double cpuPercent, long cpuTime100ns) {
    String processesJson = String.format(
      """
      {
        "pid": %d,
        "name": "upsert.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": %.1f,
        "cpu_time_100ns": %d,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "%s"
      }
      """,
      pid,
      cpuPercent,
      cpuTime100ns,
      timestamp
    );
    return payloadWithProcessArray(processesJson);
  }

  protected String payloadWithMixedValidAndInvalidProcessTimestamps() {
    String processesJson = """
      {
        "pid": 9101,
        "name": "valid-first.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": 5.0,
        "cpu_time_100ns": 100000,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "1767225600000"
      },
      {
        "pid": 9102,
        "name": "invalid-second.exe",
        "owner": "",
        "priority": "NORMAL",
        "cpu_percent": 5.0,
        "cpu_time_100ns": 200000,
        "mem_percent": 2.5,
        "location": "",
        "timestamp": "not-a-valid-timestamp"
      }
      """;
    return payloadWithProcessArray(processesJson);
  }

  protected String payloadWithProcessArray(String processesJson) {
    return String.format(
      """
      {
        "cpu": {
          "cpu_mhz": 3200,
          "cpu_usage_percent": 30,
          "system_responsiveness_percent": 95,
          "timestamp": "1767225600000"
        },
        "cpu_cores": [
          {
            "core_index": 0,
            "core_mhz": 3100,
            "core_usage_percent": 25,
            "timestamp": "1767225600000"
          }
        ],
        "memory": {
          "total_bytes": 16000000000,
          "free_bytes": 6000000000,
          "used_bytes": 10000000000,
          "memory_usage_percent": 62.5,
          "page_fault_count": 1000,
          "timestamp": "1767225600000"
        },
        "disk": [
          {
            "drive_letter": "C:",
            "total_bytes": 512000000000,
            "free_bytes": 300000000000,
            "read_speed_bytes_per_sec": 1000000,
            "write_speed_bytes_per_sec": 500000,
            "timestamp": "1767225600000"
          }
        ],
        "processes": [
          %s
        ]
      }
      """,
      processesJson
    );
  }

  protected String payload(int sampleIndex) {
    long timestampMs = 1767225600000L + sampleIndex * 1000L;
    return String.format(
      """
        {
          "cpu": {
            "cpu_mhz": %d,
            "cpu_usage_percent": %d,
            "system_responsiveness_percent": %d,
            "timestamp": "%d"
          },
          "cpu_cores": [
            {
              "core_index": 0,
              "core_mhz": %d,
              "core_usage_percent": %d,
              "timestamp": "%d"
            },
            {
              "core_index": 1,
              "core_mhz": %d,
              "core_usage_percent": %d,
              "timestamp": "%d"
            }
          ],
          "memory": {
            "total_bytes": 16000000000,
            "free_bytes": %d,
            "used_bytes": %d,
            "memory_usage_percent": %.1f,
            "page_fault_count": %d,
            "timestamp": "%d"
          },
          "disk": [
            {
              "drive_letter": "C:",
              "total_bytes": 512000000000,
              "free_bytes": %d,
              "read_speed_bytes_per_sec": %d,
              "write_speed_bytes_per_sec": %d,
              "timestamp": "%d"
            }
          ],
          "processes": [
            {
              "pid": %d,
              "name": "proc-%d.exe",
              "owner": "",
              "priority": "NORMAL",
              "cpu_percent": %.1f,
              "cpu_time_100ns": %d,
              "mem_percent": %.1f,
              "location": "",
              "timestamp": "%d"
            }
          ]
        }
        """,
      3200 + sampleIndex,
      25 + (sampleIndex % 10),
      90 + (sampleIndex % 5),
      timestampMs,
      3100 + sampleIndex,
      20 + (sampleIndex % 10),
      timestampMs,
      3300 + sampleIndex,
      30 + (sampleIndex % 10),
      timestampMs,
      6_000_000_000L - sampleIndex * 1_000_000L,
      10_000_000_000L + sampleIndex * 1_000_000L,
      62.5 + (sampleIndex % 3),
      1000 + sampleIndex,
      timestampMs,
      300_000_000_000L - sampleIndex * 100_000_000L,
      1_000_000 + sampleIndex * 100,
      500_000 + sampleIndex * 100,
      timestampMs,
      4242 + sampleIndex,
      sampleIndex,
      5.0 + (sampleIndex % 4),
      100_000L + sampleIndex * 1_000L,
      2.5 + (sampleIndex % 2),
      timestampMs
    );
  }

  protected static class TestWebSocketListener implements WebSocket.Listener {
    protected final AtomicReference<Throwable> error = new AtomicReference<>();
    protected final List<String> messages = new CopyOnWriteArrayList<>();

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      this.error.set(error);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      messages.add(data.toString());
      return WebSocket.Listener.super.onText(webSocket, data, last);
    }
  }
}
