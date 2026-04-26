package com.wasp.wasp_backend.websocket;

import com.wasp.wasp_backend.dto.CpuCoreData;
import com.wasp.wasp_backend.dto.CpuData;
import com.wasp.wasp_backend.dto.DiskData;
import com.wasp.wasp_backend.dto.MemoryData;
import com.wasp.wasp_backend.dto.ProcessData;
import com.wasp.wasp_backend.exception.JsonProcessingException;
import com.wasp.wasp_backend.service.MetricsAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class handles messages for the Metrics Websocket.
 * The ObjectMapper class from jackson is used to deserialize
 * the websocket  payload into the MetricMessage class. It
 * validates payloads coming in follow the JSON structure
 * required, and sends a payload back indicating if a field
 * is malformed or non-existent.
 *
 * @author Patrick Muller
 */
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;
  private final Set<WebSocketSession> sessions =
    ConcurrentHashMap.newKeySet();
  private final MetricsAggregationService metricsAggregationService;

  private static final Logger log =
    LoggerFactory.getLogger(MetricsWebSocketHandler.class);

  public MetricsWebSocketHandler(ObjectMapper objectMapper, MetricsAggregationService metricsAggregationService) {
    this.objectMapper = objectMapper;
    this.metricsAggregationService = metricsAggregationService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    System.out.println("Connected: " + session.getId());
  }

  @Override
  public void afterConnectionClosed(
    WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
    System.out.println("Disconnected: " + session.getId());
  }

  public void sendToAll(String json) {
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Send an error message to the client if an
   * error exists in their payload
   *
   * @param session   Current socket session
   * @param errorCode Error tile
   * @param message   Summary of error
   * @throws Exception Throw Exception if field malformed
   */
  private void sendError(WebSocketSession session,
                         String errorCode,
                         String message) throws Exception {

    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("type", "error");
    errorResponse.put("code", errorCode);
    errorResponse.put("message", message);

    String json = objectMapper.writeValueAsString(errorResponse);

    session.sendMessage(new TextMessage(json));
  }

  /**
   * This method relays the JSON payload to all currently connected clients
   *
   * @param rawJson
   * @author Patrick Muller
   */
  private void relayToFrontend(String rawJson) {
    sendToAll(rawJson);
  }

  /**
   *
   * @param node
   * @param name
   * @throws JsonProcessingException
   */
  private void requireObject(JsonNode node, String name)
    throws JsonProcessingException {
    if (node == null || node.isNull() || !node.isObject()) {
      throw new JsonProcessingException(name + " must be a JSON object") {
      };
    }
  }

  private void requireArray(JsonNode node, String name)
    throws JsonProcessingException {
    if (node == null || node.isNull() || !node.isArray()) {
      throw new JsonProcessingException(name + " must be a JSON array") {
      };
    }
  }

  private void requireFields(JsonNode node, String objectName, String... fields)
    throws JsonProcessingException {
    for (String field : fields) {
      if (!node.has(field) || node.get(field).isNull()) {
        throw new JsonProcessingException(
          "Missing required field '" + field + "' in " + objectName
        ) {
        };
      }
    }
  }

  /**
   * Validate the incoming JSON payload. Enforce all fields are present
   * and in the form that is expected.
   *
   * @param cpuData     Cpu field
   * @param cpuCoreData Cpu core field
   * @param memoryData  Memory field
   * @param diskData    Disk field
   * @param processData Process field
   */
  private void validateMetricData(JsonNode cpuData,
                                  JsonNode cpuCoreData,
                                  JsonNode memoryData,
                                  JsonNode diskData,
                                  JsonNode processData)
    throws JsonProcessingException {

    requireObject(cpuData, "cpu");
    requireFields(cpuData, "cpu",
      "cpu_mhz",
      "cpu_usage_percent",
      "system_responsiveness_percent",
      "timestamp"
    );

    // ---- CPU CORES (array of objects) ----
    requireArray(cpuCoreData, "cpu_cores");
    if (cpuCoreData.size() == 0) {
      throw new JsonProcessingException("cpu_cores array must not be empty") {
      };
    }

    for (int i = 0; i < cpuCoreData.size(); i++) {
      JsonNode core = cpuCoreData.get(i);
      requireObject(core, "cpu_cores[" + i + "]");
      requireFields(core, "cpu_cores[" + i + "]",
        "core_index",
        "core_usage_percent",
        "timestamp"
      );
    }

    // ---- MEMORY (single object) ----
    requireObject(memoryData, "memory");
    requireFields(memoryData, "memory",
      "total_bytes",
      "free_bytes",
      "used_bytes",
      "memory_usage_percent",
      "page_fault_count",
      "timestamp"
    );

    // ---- DISK (array of objects) ----
    requireArray(diskData, "disk");
    if (diskData.size() == 0) {
      throw new JsonProcessingException("disk array must not be empty") {
      };
    }

    for (int i = 0; i < diskData.size(); i++) {
      JsonNode disk = diskData.get(i);
      requireObject(disk, "disk[" + i + "]");
      requireFields(disk, "disk[" + i + "]",
        "drive_letter",
        "total_bytes",
        "free_bytes",
        "read_speed_bytes_per_sec",
        "write_speed_bytes_per_sec",
        "timestamp"
      );
    }

    // ---- PROCESSES (array of objects) ----
    if (processData != null && !processData.isMissingNode() && !processData.isNull()) {
      requireArray(processData, "processes");
      for (int i = 0; i < processData.size(); i++) {
        JsonNode process = processData.get(i);
        requireObject(process, "processes[" + i + "]");
        requireFields(process, "processes[" + i + "]",
          "pid",
          "name",
          "cpu_percent",
          "cpu_time_100ns",
          "timestamp"
        );
      }
    }
  }

  /**
   * This method is called upon when a message is received
   * on the metrics web socket. It deserializes the message
   * into the MetricMessage class and calls validateMetricMessage.
   * If any errors are detected it invokes sendError.
   *
   * @param session Current socket session
   * @param message Incoming socket message
   * @throws Exception Throws exception upon error in formatting
   */
  @Override
  public void handleTextMessage(WebSocketSession session,
                                TextMessage message) throws Exception {

    String rawJson = message.getPayload();

    try {
      // This method will relay deserialized JSON payload directly to frontend
      relayToFrontend(rawJson);

      JsonNode root = objectMapper.readTree(rawJson);

      JsonNode cpuNode = root.path("cpu");
      JsonNode cpuCoresNode = root.path("cpu_cores");
      JsonNode memoryNode = root.path("memory");
      JsonNode diskNode = root.path("disk");
      JsonNode processesNode = root.path("processes");
      boolean hasProcesses = !processesNode.isMissingNode() && !processesNode.isNull();

      // validation occurs after relay to reduce overhead
      validateMetricData(cpuNode, cpuCoresNode, memoryNode, diskNode, processesNode);

      CpuData cpu = objectMapper.treeToValue(cpuNode, CpuData.class);

      List<CpuCoreData> cpuCores =
        objectMapper.convertValue(cpuCoresNode,
          new TypeReference<>() {
          });

      MemoryData memory = objectMapper.treeToValue(memoryNode, MemoryData.class);

      List<DiskData> disk = objectMapper.convertValue(diskNode,
        new TypeReference<>() {
        });

      if (hasProcesses) {
        List<ProcessData> processes = objectMapper.convertValue(processesNode,
          new TypeReference<>() {
          });
        metricsAggregationService.ingest(cpu, cpuCores, memory, disk, processes);
      } else {
        metricsAggregationService.ingest(cpu, cpuCores, memory, disk);
      }

    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      sendError(session, "INVALID_FORMAT", e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      sendError(session, "INVALID_JSON", "Malformed JSON payload");
    }
  }

}
