using WaspDesktop.Models;

namespace WaspDesktop.Services;

public sealed class MetricsState : IDisposable
{
    private readonly MetricsStreamService _stream = new();
    private readonly AppSettingsService _settings;
    private DateTimeOffset _lastUiUpdate = DateTimeOffset.MinValue;

    public MetricsSnapshot? LatestSnapshot { get; private set; }
    public string ConnectionStatus { get; private set; } = "Connecting...";

    public event EventHandler? Changed;
    public event EventHandler<string>? ConnectionStatusChanged;
    public event EventHandler<BackendNotification>? BackendNotificationReceived;

    public MetricsState(AppSettingsService settings)
    {
        _settings = settings;
        _stream.SnapshotReceived += (_, snapshot) =>
        {
            LatestSnapshot = snapshot;
            var now = DateTimeOffset.Now;
            if ((now - _lastUiUpdate).TotalSeconds >= _settings.RefreshIntervalSeconds)
            {
                _lastUiUpdate = now;
                Changed?.Invoke(this, EventArgs.Empty);
            }
        };

        _stream.ConnectionStatusChanged += (_, status) =>
        {
            if (ConnectionStatus == status)
            {
                return;
            }

            ConnectionStatus = status;
            ConnectionStatusChanged?.Invoke(this, status);
            Changed?.Invoke(this, EventArgs.Empty);
        };

        _stream.BackendNotificationReceived += (_, notification) =>
        {
            BackendNotificationReceived?.Invoke(this, notification);
        };

        _settings.SettingsChanged += (_, _) =>
        {
            // Force refresh after setting changes (e.g., interval).
            _lastUiUpdate = DateTimeOffset.MinValue;
            Changed?.Invoke(this, EventArgs.Empty);
        };
    }

    public void Start() => _stream.Start();

    public void Dispose() => _stream.Dispose();
}
