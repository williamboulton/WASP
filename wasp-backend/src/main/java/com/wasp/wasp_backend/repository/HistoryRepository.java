package com.wasp.wasp_backend.repository;

import com.wasp.wasp_backend.dto.HistoryData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class HistoryRepository {

  private final JdbcTemplate jdbc;

  public HistoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public HistoryData getHistoryData(long timePeriod) {

    String sql = """
            WITH
            cpu_avg AS (
                SELECT
                    AVG(cpu_ghz) AS avg_cpu_ghz,
                    AVG(cpu_usage) AS avg_cpu_usage
                FROM cpu
                WHERE timestamp_ms >= ?
            ),
            core_avg AS (
                SELECT
                    AVG(core_ghz) AS avg_core_ghz,
                    AVG(core_usage) AS avg_core_usage
                FROM cpu_cores
                WHERE timestamp_ms >= ?
            ),
            memory_avg AS (
                SELECT
                    AVG(total_mem) AS avg_total_mem,
                    AVG(free_mem) AS avg_free_mem,
                    AVG(used_mem) AS avg_used_mem,
                    AVG(mem_usage) AS avg_mem_usage,
                    AVG(page_faults) AS avg_page_faults
                FROM memory
                WHERE timestamp_ms >= ?
            ),
            disk_avg AS (
                SELECT
                    AVG(total_space) AS avg_total_disk_space,
                    AVG(free_space) AS avg_free_disk_space,
                    AVG(read_speed) AS avg_read_speed,
                    AVG(write_speed) AS avg_write_speed
                FROM disk
                WHERE timestamp_ms >= ?
            )
            SELECT
                cpu_avg.avg_cpu_ghz,
                cpu_avg.avg_cpu_usage,

                core_avg.avg_core_ghz,
                core_avg.avg_core_usage,

                memory_avg.avg_total_mem,
                memory_avg.avg_free_mem,
                memory_avg.avg_used_mem,
                memory_avg.avg_mem_usage,
                memory_avg.avg_page_faults,

                disk_avg.avg_total_disk_space,
                disk_avg.avg_free_disk_space,
                disk_avg.avg_read_speed,
                disk_avg.avg_write_speed
            FROM cpu_avg
            CROSS JOIN core_avg
            CROSS JOIN memory_avg
            CROSS JOIN disk_avg
            """;

    return jdbc.queryForObject(
      sql,
      this::mapHistoryData,
      timePeriod,
      timePeriod,
      timePeriod,
      timePeriod
    );
  }

  private HistoryData mapHistoryData(ResultSet rs, int rowNum) throws SQLException {
    HistoryData data = new HistoryData();

    data.setAvgCpuGhz(rs.getDouble("avg_cpu_ghz"));
    data.setAvgCpuUsage(rs.getDouble("avg_cpu_usage"));

    data.setAvgCoreGhz(rs.getDouble("avg_core_ghz"));
    data.setAvgCoreUsage(rs.getDouble("avg_core_usage"));

    data.setAvgTotalMem(rs.getDouble("avg_total_mem"));
    data.setAvgFreeMem(rs.getDouble("avg_free_mem"));
    data.setAvgUsedMem(rs.getDouble("avg_used_mem"));
    data.setAvgMemUsage(rs.getDouble("avg_mem_usage"));
    data.setAvgPageFaults(rs.getDouble("avg_page_faults"));

    data.setAvgTotalDiskSpace(rs.getDouble("avg_total_disk_space"));
    data.setAvgFreeDiskSpace(rs.getDouble("avg_free_disk_space"));
    data.setAvgReadSpeed(rs.getDouble("avg_read_speed"));
    data.setAvgWriteSpeed(rs.getDouble("avg_write_speed"));

    return data;
  }
}
