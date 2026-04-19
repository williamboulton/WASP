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
class MetricsWebSocketBlackBoxPersistenceTest extends MetricsWebSocketBlackBoxTestSupport {

  @Test
  void websocketPersistsOneDiskRowPerDriveAtWindowBoundary() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      for (int i = 0; i < 60; i++) {
        webSocket.sendText(payloadWithTwoDisks(i), true).join();
      }

      assertTrue(waitForProcessCount(60, 5000), "Timed out waiting for process DB writes");
      assertTrue(waitForCpuAggregate(5000), "Timed out waiting for aggregate DB writes");

      long ts = 1767225600000L;
      Integer diskCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM disk WHERE timestamp_ms = ?",
        Integer.class,
        ts
      );
      assertEquals(2, diskCount);

      Long cFreeSpace = jdbcTemplate.queryForObject(
        "SELECT free_space FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Long.class,
        ts,
        "C:"
      );
      Double cReadSpeed = jdbcTemplate.queryForObject(
        "SELECT read_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Double.class,
        ts,
        "C:"
      );
      Double cWriteSpeed = jdbcTemplate.queryForObject(
        "SELECT write_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Double.class,
        ts,
        "C:"
      );
      assertEquals(297_050_000_000L, cFreeSpace);
      assertEquals(1_002_950.0, cReadSpeed);
      assertEquals(502_950.0, cWriteSpeed);

      Long dFreeSpace = jdbcTemplate.queryForObject(
        "SELECT free_space FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Long.class,
        ts,
        "D:"
      );
      Double dReadSpeed = jdbcTemplate.queryForObject(
        "SELECT read_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Double.class,
        ts,
        "D:"
      );
      Double dWriteSpeed = jdbcTemplate.queryForObject(
        "SELECT write_speed FROM disk WHERE timestamp_ms = ? AND drive_letter = ?",
        Double.class,
        ts,
        "D:"
      );
      assertEquals(698_525_000_000L, dFreeSpace);
      assertEquals(2_001_475.0, dReadSpeed);
      assertEquals(1_251_475.0, dWriteSpeed);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketMapsPriorityLabelsAndUnknownValuesIntoDatabase() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadWithPriorityMatrix(), true).join();
      assertTrue(waitForProcessCount(4, 5000), "Timed out waiting for process DB writes");

      Integer highPriority = jdbcTemplate.queryForObject(
        "SELECT priority FROM processes WHERE pid = ?",
        Integer.class,
        8001
      );
      Integer realtimePriority = jdbcTemplate.queryForObject(
        "SELECT priority FROM processes WHERE pid = ?",
        Integer.class,
        8002
      );
      Integer numericPriority = jdbcTemplate.queryForObject(
        "SELECT priority FROM processes WHERE pid = ?",
        Integer.class,
        8003
      );
      Integer unknownPriority = jdbcTemplate.queryForObject(
        "SELECT priority FROM processes WHERE pid = ?",
        Integer.class,
        8004
      );

      Integer cpuAggregates = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cpu", Integer.class);

      assertEquals(13, highPriority);
      assertEquals(24, realtimePriority);
      assertEquals(10, numericPriority);
      assertNull(unknownPriority);
      assertEquals(0, cpuAggregates);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketNormalizesTimestampFormatsToSameProcessPrimaryKey() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadWithTimestampFormatVariants(), true).join();
      assertTrue(waitForProcessCount(1, 5000), "Timed out waiting for process DB writes");

      Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Integer.class,
        1735689600000L,
        9001
      );
      Double cpuPercent = jdbcTemplate.queryForObject(
        "SELECT cpu_percent FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Double.class,
        1735689600000L,
        9001
      );
      Long cpuTime = jdbcTemplate.queryForObject(
        "SELECT cpu_time FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Long.class,
        1735689600000L,
        9001
      );

      assertEquals(1, count);
      assertEquals(7.0, cpuPercent);
      assertEquals(300000L, cpuTime);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketReplacesExistingProcessRowWhenTimestampAndPidMatch() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadWithSingleProcess("2026-01-01T00:00:00Z", 9010, 4.0, 100000L), true).join();
      webSocket.sendText(payloadWithSingleProcess("2026-01-01T00:00:00Z", 9010, 9.0, 900000L), true).join();
      assertTrue(waitForProcessCount(1, 5000), "Timed out waiting for process DB writes");

      Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Integer.class,
        1767225600000L,
        9010
      );
      Double cpuPercent = jdbcTemplate.queryForObject(
        "SELECT cpu_percent FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Double.class,
        1767225600000L,
        9010
      );
      Long cpuTime = jdbcTemplate.queryForObject(
        "SELECT cpu_time FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Long.class,
        1767225600000L,
        9010
      );

      assertEquals(1, count);
      assertEquals(9.0, cpuPercent);
      assertEquals(900000L, cpuTime);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }

  @Test
  void websocketPersistsEarlierValidProcessesWhenLaterProcessInBatchHasInvalidTimestamp() throws Exception {
    TestWebSocketListener listener = new TestWebSocketListener();
    WebSocket webSocket = openSocket(listener);

    try {
      webSocket.sendText(payloadWithMixedValidAndInvalidProcessTimestamps(), true).join();
      assertTrue(waitForProcessCount(1, 5000), "Timed out waiting for process DB writes");
      assertTrue(
        waitForMessageContains(listener, "\"code\":\"INVALID_FORMAT\"", 3000),
        "Expected INVALID_FORMAT error code for invalid timestamp"
      );

      Integer totalProcessCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processes", Integer.class);
      Integer validRowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processes WHERE timestamp_ms = ? AND pid = ?",
        Integer.class,
        1767225600000L,
        9101
      );
      Integer invalidRowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM processes WHERE pid = ?",
        Integer.class,
        9102
      );

      assertEquals(1, totalProcessCount);
      assertEquals(1, validRowCount);
      assertEquals(0, invalidRowCount);
      assertNull(listener.error.get(), () -> "WebSocket listener error: " + listener.error.get());
    } finally {
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }
  }
}
