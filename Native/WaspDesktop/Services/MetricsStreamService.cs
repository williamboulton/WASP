using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using WaspDesktop.Models;

namespace WaspDesktop.Services;

public sealed class MetricsStreamService : IDisposable
{
    private readonly Uri _endpoint = new("ws://localhost:8080/ws/metrics");
    private readonly CancellationTokenSource _lifetimeCts = new();
    private readonly JsonSerializerOptions _jsonOptions = new(JsonSerializerDefaults.Web);
    private readonly object _connectionLock = new();
    private Task? _backgroundTask;

    public event EventHandler<MetricsSnapshot>? SnapshotReceived;
    public event EventHandler<BackendNotification>? BackendNotificationReceived;
    public event EventHandler<string>? ConnectionStatusChanged;

    public void Start()
    {
        lock (_connectionLock)
        {
            if (_backgroundTask is { IsCompleted: false })
            {
                return;
            }

            _backgroundTask = Task.Run(() => RunLoopAsync(_lifetimeCts.Token));
        }
    }

    private async Task RunLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            using var socket = new ClientWebSocket();

            try
            {
                OnConnectionStatusChanged("Connecting...");
                await socket.ConnectAsync(_endpoint, cancellationToken);
                OnConnectionStatusChanged("Connected");
                await ReceiveLoopAsync(socket, cancellationToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception)
            {
                OnConnectionStatusChanged("Disconnected - retrying...");
                await Task.Delay(TimeSpan.FromSeconds(3), cancellationToken);
            }
        }
    }

    private async Task ReceiveLoopAsync(ClientWebSocket socket, CancellationToken cancellationToken)
    {
        var buffer = new byte[8192];

        while (!cancellationToken.IsCancellationRequested && socket.State == WebSocketState.Open)
        {
            using var payload = new MemoryStream();
            ValueWebSocketReceiveResult result;

            do
            {
                result = await socket.ReceiveAsync(buffer.AsMemory(0, buffer.Length), cancellationToken);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    OnConnectionStatusChanged("Disconnected - retrying...");
                    return;
                }

                payload.Write(buffer, 0, result.Count);
            } while (!result.EndOfMessage);

            var json = Encoding.UTF8.GetString(payload.ToArray());
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                continue;
            }

            if (root.TryGetProperty("type", out var typeNode) &&
                typeNode.ValueKind == JsonValueKind.String &&
                string.Equals(typeNode.GetString(), "backend_notification", StringComparison.OrdinalIgnoreCase))
            {
                var notification = new BackendNotification
                {
                    Severity = root.TryGetProperty("severity", out var severityNode) ? severityNode.GetString() ?? "info" : "info",
                    Category = root.TryGetProperty("category", out var categoryNode) ? categoryNode.GetString() ?? "general" : "general",
                    Title = root.TryGetProperty("title", out var titleNode) ? titleNode.GetString() ?? "Backend" : "Backend",
                    Message = root.TryGetProperty("message", out var messageNode) ? messageNode.GetString() ?? string.Empty : string.Empty
                };
                BackendNotificationReceived?.Invoke(this, notification);
                continue;
            }

            var snapshot = JsonSerializer.Deserialize<MetricsSnapshot>(json, _jsonOptions);
            if (snapshot is not null && (snapshot.Cpu is not null || snapshot.Memory is not null || snapshot.Disk is not null || snapshot.CpuCores is not null || snapshot.Processes is not null))
            {
                SnapshotReceived?.Invoke(this, snapshot);
            }
        }
    }

    private void OnConnectionStatusChanged(string status)
    {
        ConnectionStatusChanged?.Invoke(this, status);
    }

    public void Dispose()
    {
        _lifetimeCts.Cancel();
        _lifetimeCts.Dispose();
    }
}
