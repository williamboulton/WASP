using WaspDesktop.Models;

namespace WaspDesktop.Services;

public sealed class MetricsState : IDisposable
{
    private readonly MetricsStreamService _stream = new();

    public MetricsSnapshot? LatestSnapshot { get; private set; }
    public string ConnectionStatus { get; private set; } = "Connecting...";

    public event EventHandler? Changed;

    public MetricsState()
    {
        _stream.SnapshotReceived += (_, snapshot) =>
        {
            LatestSnapshot = snapshot;
            Changed?.Invoke(this, EventArgs.Empty);
        };

        _stream.ConnectionStatusChanged += (_, status) =>
        {
            ConnectionStatus = status;
            Changed?.Invoke(this, EventArgs.Empty);
        };
    }

    public void Start() => _stream.Start();

    public void Dispose() => _stream.Dispose();
}
