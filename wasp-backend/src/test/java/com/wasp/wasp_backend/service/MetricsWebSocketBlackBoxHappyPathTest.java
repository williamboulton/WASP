package com.wasp.wasp_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.WebSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MetricsWebSocketBlackBoxHappyPathTest extends MetricsWebSocketBlackBoxTestSupport {

  @Test
  void websocketPayloadsProduceExpectedAggregatesInDatabase() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      sendSamples(webSocket, 0, 60);

      assertTrue(waitForProcessCount(1, 5000), "Timed out waiting for process DB writes");
      assertTrue(waitForCpuAggregate(5000), "Timed out waiting for aggregate DB writes");
      assertTrue(waitForDiskCount(1, 5000), "Timed out waiting for disk aggregate writes");

      Long ts = 1767225600000L;

      Integer cpuCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM cpu WHERE timestamp_ms = ?",
        Integer.class,
        ts
      );
      Integer coreCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM cpu_cores WHERE timestamp_ms = ?",
        Integer.class,
        ts
      );
      Integer memoryCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM memory WHERE timestamp_ms = ?",
        Integer.class,
        ts
      );
      Integer diskCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM disk WHERE timestamp_ms = ?",
        Integer.class,
        ts
      );
      Integer processCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processes",
        Integer.class
      );
      assertEquals(1, cpuCount);
      assertEquals(2, coreCount);
      assertEquals(1, memoryCount);
      assertEquals(1, diskCount);
      assertEquals(1, processCount);

      Double cpuGhz = jdbcTemplate.queryForObject(
        "SELECT cpu_ghz FROM cpu WHERE timestamp_ms = ?",
        Double.class,
        ts
      );
      Double cpuUsage = jdbcTemplate.queryForObject(
        "SELECT cpu_usage FROM cpu WHERE timestamp_ms = ?",
        Double.class,
        ts
      );
      assertEquals(3.2, cpuGhz);
      assertEquals(29.5, cpuUsage);

      Double core0Ghz = jdbcTemplate.queryForObject(
        "SELECT core_ghz FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
        Double.class,
        ts,
        0
      );
      Double core0Usage = jdbcTemplate.queryForObject(
        "SELECT core_usage FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
        Double.class,
        ts,
        0
      );
      Double core1Ghz = jdbcTemplate.queryForObject(
        "SELECT core_ghz FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
        Double.class,
        ts,
        1
      );
      Double core1Usage = jdbcTemplate.queryForObject(
        "SELECT core_usage FROM cpu_cores WHERE timestamp_ms = ? AND core_index = ?",
        Double.class,
        ts,
        1
      );
      assertEquals(3.1, core0Ghz);
      assertEquals(24.5, core0Usage);
      assertEquals(3.3, core1Ghz);
      assertEquals(34.5, core1Usage);

      Long totalMem = jdbcTemplate.queryForObject(
        "SELECT total_mem FROM memory WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      Long freeMem = jdbcTemplate.queryForObject(
        "SELECT free_mem FROM memory WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      Long usedMem = jdbcTemplate.queryForObject(
        "SELECT used_mem FROM memory WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      Double memUsage = jdbcTemplate.queryForObject(
        "SELECT mem_usage FROM memory WHERE timestamp_ms = ?",
        Double.class,
        ts
      );
      Long pageFaults = jdbcTemplate.queryForObject(
        "SELECT page_faults FROM memory WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      assertEquals(16_000_000_000L, totalMem);
      assertEquals(5_995_500_000L, freeMem);
      assertEquals(10_004_500_000L, usedMem);
      assertEquals(63.4, memUsage);
      assertEquals(1_005L, pageFaults);

      String driveLetter = jdbcTemplate.queryForObject(
        "SELECT drive_letter FROM disk WHERE timestamp_ms = ?",
        String.class,
        ts
      );
      Long totalSpace = jdbcTemplate.queryForObject(
        "SELECT total_space FROM disk WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      Long freeSpace = jdbcTemplate.queryForObject(
        "SELECT free_space FROM disk WHERE timestamp_ms = ?",
        Long.class,
        ts
      );
      Double readSpeed = jdbcTemplate.queryForObject(
        "SELECT read_speed FROM disk WHERE timestamp_ms = ?",
        Double.class,
        ts
      );
      Double writeSpeed = jdbcTemplate.queryForObject(
        "SELECT write_speed FROM disk WHERE timestamp_ms = ?",
        Double.class,
        ts
      );
      assertEquals("C:", driveLetter);
      assertEquals(512_000_000_000L, totalSpace);
      assertEquals(299_550_000_000L, freeSpace);
      assertEquals(1_000_450.0, readSpeed);
      assertEquals(500_450.0, writeSpeed);

      Integer priority = jdbcTemplate.queryForObject(
        "SELECT priority FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Integer.class,
        1767225659000L,
        4301
      );
      String owner = jdbcTemplate.queryForObject(
        "SELECT owner FROM processes WHERE timestamp_ms = ? AND pid = ?",
        String.class,
        1767225659000L,
        4301
      );
      assertEquals(8, priority);
      assertNull(owner);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketDoesNotEmitAggregateBeforeWindowBoundary() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      sendSamples(webSocket, 0, 59);
      Thread.sleep(200);

      Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
      Integer coreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu_cores", Integer.class);
      Integer memoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory", Integer.class);
      Integer diskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM disk", Integer.class);
      Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);

      assertEquals(5, cpuCount);
      assertEquals(10, coreCount);
      assertEquals(5, memoryCount);
      assertEquals(5, diskCount);
      assertEquals(0, processCount);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketEmitsAggregatesForEachCompletedWindow() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      sendSamples(webSocket, 0, 120);

      assertTrue(waitForProcessCount(2, 5000), "Timed out waiting for process DB writes");
      assertTrue(waitForCpuAggregateCount(12, 5000), "Timed out waiting for aggregate windows");
      assertTrue(waitForDiskCount(12, 5000), "Timed out waiting for disk aggregate windows");

      Integer cpuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);
      Integer coreCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu_cores", Integer.class);
      Integer memoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory", Integer.class);
      Integer diskCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM disk", Integer.class);
      Integer processCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);

      assertEquals(12, cpuCount);
      assertEquals(24, coreCount);
      assertEquals(12, memoryCount);
      assertEquals(12, diskCount);
      assertEquals(2, processCount);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());

      Integer window1CpuCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM cpu WHERE timestamp_ms = ?",
        Integer.class,
        1767225600000L
      );
      Integer window2CpuCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM cpu WHERE timestamp_ms = ?",
        Integer.class,
        1767225610000L
      );
      assertEquals(1, window1CpuCount);
      assertEquals(1, window2CpuCount);
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }
}
