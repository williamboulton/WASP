using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using WaspDesktop.Models;
using WaspDesktop.Services;

namespace WaspDesktop.Pages;

public sealed partial class DiskPage : Page
{
    private MetricsState? _metricsState;

    public DiskPage()
    {
        InitializeComponent();
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _metricsState = ((App)Application.Current).MetricsState;
        _metricsState.Changed += MetricsStateOnChanged;
        Render();
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        if (_metricsState is not null)
        {
            _metricsState.Changed -= MetricsStateOnChanged;
        }

        base.OnNavigatedFrom(e);
    }

    private void MetricsStateOnChanged(object? sender, EventArgs e)
    {
        DispatcherQueue.TryEnqueue(Render);
    }

    private void Render()
    {
        var diskItems = (_metricsState?.LatestSnapshot?.Disk ?? [])
            .Select(DiskCardItem.FromSnapshot)
            .ToList();

        DiskList.ItemsSource = diskItems;
    }
}

public sealed class DiskCardItem
{
    public string Name { get; init; } = string.Empty;
    public double UsedPercent { get; init; }
    public string UsedPercentText { get; init; } = string.Empty;
    public string Capacity { get; init; } = string.Empty;
    public List<DetailRow> Details { get; init; } = [];

    public static DiskCardItem FromSnapshot(DiskSnapshot snapshot)
    {
        var total = snapshot.TotalBytes ?? 0;
        var free = snapshot.FreeBytes ?? 0;
        var used = Math.Max(0, total - free);
        var usedPercent = total > 0 ? Math.Clamp((used * 100d) / total, 0, 100) : 0;

        var driveLabel = FormatDrive(snapshot.DriveLetter);

        return new DiskCardItem
        {
            Name = driveLabel,
            UsedPercent = usedPercent,
            UsedPercentText = $"{usedPercent:F1}% used",
            Capacity = $"{Formatters.GiB(used)} / {Formatters.GiB(snapshot.TotalBytes)}",
            Details =
            [
                new DetailRow("Drive Letter", driveLabel),
                new DetailRow("Total Bytes", $"{Formatters.Integer(snapshot.TotalBytes)} ({Formatters.GiB(snapshot.TotalBytes)})"),
                new DetailRow("Free Bytes", $"{Formatters.Integer(snapshot.FreeBytes)} ({Formatters.GiB(snapshot.FreeBytes)})"),
                new DetailRow("Read Speed Bytes Per Sec", $"{Formatters.Integer(snapshot.ReadBytesPerSec)} ({Formatters.Rate(snapshot.ReadBytesPerSec)})"),
                new DetailRow("Write Speed Bytes Per Sec", $"{Formatters.Integer(snapshot.WriteBytesPerSec)} ({Formatters.Rate(snapshot.WriteBytesPerSec)})")
            ]
        };
    }

    private static string FormatDrive(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
        {
            return "Drive";
        }

        var normalized = raw.Trim();
        return normalized.EndsWith(":", StringComparison.Ordinal) ? normalized : $"{normalized}:";
    }
}

public sealed record DetailRow(string Label, string Value);
