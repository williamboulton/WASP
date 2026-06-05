using Microsoft.UI.Xaml;
using WaspDesktop.Services;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace WaspDesktop;

/// <summary>
/// Provides application-specific behavior to supplement the default Application class.
/// </summary>
public partial class App : Application
{
    private Window? _window;
    public AppSettingsService Settings { get; } = new();
    public MetricsState MetricsState { get; }
    public NotificationCenter NotificationCenter { get; } = new();
    private string _lastConnectionNotification = string.Empty;

    /// <summary>
    /// Initializes the singleton application object.  This is the first line of authored code
    /// executed, and as such is the logical equivalent of main() or WinMain().
    /// </summary>
    public App()
    {
        InitializeComponent();
        MetricsState = new MetricsState(Settings);
    }

    /// <summary>
    /// Invoked when the application is launched.
    /// </summary>
    /// <param name="args">Details about the launch request and process.</param>
    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        MetricsState.ConnectionStatusChanged += MetricsStateOnConnectionStatusChanged;
        MetricsState.BackendNotificationReceived += MetricsStateOnBackendNotificationReceived;
        MetricsState.Start();
        _window = new MainWindow();
        NotificationCenter.InitializeDispatcher(_window.DispatcherQueue);
        _window.Closed += (_, _) =>
        {
            MetricsState.ConnectionStatusChanged -= MetricsStateOnConnectionStatusChanged;
            MetricsState.BackendNotificationReceived -= MetricsStateOnBackendNotificationReceived;
            MetricsState.Dispose();
        };
        _window.Activate();
    }

    private void MetricsStateOnConnectionStatusChanged(object? sender, string status)
    {
        if (_lastConnectionNotification == status)
        {
            return;
        }

        _lastConnectionNotification = status;

        if (status.Equals("Connected", StringComparison.OrdinalIgnoreCase))
        {
            NotificationCenter.Notify(NotificationSeverity.Info, "Backend Connection", "Connected to backend.");
            return;
        }

        if (status.Contains("Disconnected", StringComparison.OrdinalIgnoreCase))
        {
            NotificationCenter.Notify(NotificationSeverity.Error, "Backend Connection", status);
            return;
        }

        if (status.Contains("Error", StringComparison.OrdinalIgnoreCase))
        {
            NotificationCenter.Notify(NotificationSeverity.Error, "Backend Connection", status);
        }
    }

    private void MetricsStateOnBackendNotificationReceived(object? sender, BackendNotification notification)
    {
        var category = notification.Category ?? string.Empty;
        var alwaysShow = category.Equals("connection", StringComparison.OrdinalIgnoreCase);
        if (!alwaysShow && !Settings.ExtraNotificationsEnabled)
        {
            return;
        }

        var severity = notification.Severity.ToLowerInvariant() switch
        {
            "error" => NotificationSeverity.Error,
            "warning" => NotificationSeverity.Warning,
            "success" => NotificationSeverity.Success,
            _ => NotificationSeverity.Info
        };

        NotificationCenter.Notify(severity, notification.Title, notification.Message);
    }
}
