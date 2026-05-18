using System.Text.Json.Serialization;

namespace WaspDesktop.Models;

public sealed class MetricsSnapshot
{
    [JsonPropertyName("cpu")]
    public CpuSnapshot? Cpu { get; init; }

    [JsonPropertyName("memory")]
    public MemorySnapshot? Memory { get; init; }

    [JsonPropertyName("cpu_cores")]
    public List<CpuCoreSnapshot>? CpuCores { get; init; }

    [JsonPropertyName("disk")]
    public List<DiskSnapshot>? Disk { get; init; }

    [JsonPropertyName("processes")]
    public List<ProcessSnapshot>? Processes { get; init; }
}

public sealed class CpuSnapshot
{
    [JsonPropertyName("timestamp")]
    public long? Timestamp { get; init; }

    [JsonPropertyName("cpu_usage_percent")]
    public double? CpuUsagePercent { get; init; }

    [JsonPropertyName("cpu_mhz")]
    public double? CpuMhz { get; init; }

    [JsonPropertyName("system_responsiveness_percent")]
    public double? SystemResponsivenessPercent { get; init; }
}

public sealed class CpuCoreSnapshot
{
    [JsonPropertyName("core_index")]
    public int? CoreIndex { get; init; }

    [JsonPropertyName("core_usage_percent")]
    public double? CoreUsagePercent { get; init; }

    [JsonPropertyName("core_mhz")]
    public double? CoreMhz { get; init; }
}

public sealed class MemorySnapshot
{
    [JsonPropertyName("timestamp")]
    public long? Timestamp { get; init; }

    [JsonPropertyName("memory_usage_percent")]
    public double? MemoryUsagePercent { get; init; }

    [JsonPropertyName("used_bytes")]
    public long? UsedBytes { get; init; }

    [JsonPropertyName("total_bytes")]
    public long? TotalBytes { get; init; }

    [JsonPropertyName("free_bytes")]
    public long? FreeBytes { get; init; }

    [JsonPropertyName("page_fault_count")]
    public long? PageFaultCount { get; init; }
}

public sealed class DiskSnapshot
{
    [JsonPropertyName("timestamp")]
    public long? Timestamp { get; init; }

    [JsonPropertyName("drive_letter")]
    public string? DriveLetter { get; init; }

    [JsonPropertyName("total_bytes")]
    public long? TotalBytes { get; init; }

    [JsonPropertyName("free_bytes")]
    public long? FreeBytes { get; init; }

    [JsonPropertyName("read_speed_bytes_per_sec")]
    public long? ReadBytesPerSec { get; init; }

    [JsonPropertyName("write_speed_bytes_per_sec")]
    public long? WriteBytesPerSec { get; init; }
}

public sealed class ProcessSnapshot
{
    [JsonPropertyName("name")]
    public string? Name { get; init; }

    [JsonPropertyName("owner")]
    public string? Owner { get; init; }

    [JsonPropertyName("pid")]
    public int? Pid { get; init; }

    [JsonPropertyName("priority")]
    public string? Priority { get; init; }

    [JsonPropertyName("cpu_percent")]
    public double? CpuPercent { get; init; }

    [JsonPropertyName("location")]
    public string? Location { get; init; }
}
