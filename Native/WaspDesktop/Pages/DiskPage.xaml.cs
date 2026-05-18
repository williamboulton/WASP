using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
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
            .Select(d =>
            {
                var usedBytes = Math.Max(0, (d.TotalBytes ?? 0) - (d.FreeBytes ?? 0));
                return new DiskListItem(
                    d.DriveLetter ?? "Drive",
                    $"{Formatters.GiB(usedBytes)} / {Formatters.GiB(d.TotalBytes)} used",
                    $"Read {Formatters.Rate(d.ReadBytesPerSec)} | Write {Formatters.Rate(d.WriteBytesPerSec)}"
                );
            })
            .ToList();

        DiskList.ItemsSource = diskItems;
    }
}

public sealed record DiskListItem(string Name, string Capacity, string Throughput);
