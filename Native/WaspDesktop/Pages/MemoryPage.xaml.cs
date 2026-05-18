using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using WaspDesktop.Services;

namespace WaspDesktop.Pages;

public sealed partial class MemoryPage : Page
{
    private MetricsState? _metricsState;

    public MemoryPage()
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
        var memory = _metricsState?.LatestSnapshot?.Memory;
        var percent = memory?.MemoryUsagePercent ?? 0;
        MemoryUsageBar.Value = Math.Clamp(percent, 0, 100);

        var used = Formatters.GiB(memory?.UsedBytes);
        var total = Formatters.GiB(memory?.TotalBytes);
        MemorySummaryText.Text = $"{Formatters.Percent(memory?.MemoryUsagePercent)} ({used} / {total})";
    }
}
