using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Microsoft.UI.Xaml;
using WaspDesktop.Models;
using WaspDesktop.Services;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace WaspDesktop.Pages;

public sealed partial class HomePage : Page
{
    private MetricsState? _metricsState;

    public HomePage()
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
        if (_metricsState is null)
        {
            return;
        }

        var snapshot = _metricsState.LatestSnapshot;
        ConnectionStatusText.Text = _metricsState.ConnectionStatus;
        CpuUsageText.Text = Formatters.Percent(snapshot?.Cpu?.CpuUsagePercent);
        CpuClockText.Text = Formatters.ClockSpeed(snapshot?.Cpu?.CpuMhz);
        ResponsivenessText.Text = Formatters.Percent(snapshot?.Cpu?.SystemResponsivenessPercent);

        var memoryPercent = Formatters.Percent(snapshot?.Memory?.MemoryUsagePercent);
        var used = Formatters.GiB(snapshot?.Memory?.UsedBytes);
        var total = Formatters.GiB(snapshot?.Memory?.TotalBytes);
        MemoryText.Text = $"{memoryPercent} ({used} / {total})";

        var items = (snapshot?.Processes ?? [])
            .Where(p => (p.CpuPercent ?? 0) > 0)
            .OrderByDescending(p => p.CpuPercent ?? 0)
            .Take(20)
            .Select(p => new ProcessListItem(p))
            .ToList();
        ProcessList.ItemsSource = items;
    }
}

public sealed class ProcessListItem(ProcessSnapshot source)
{
    public string Name { get; } = source.Name ?? string.Empty;
    public string Owner { get; } = source.Owner ?? string.Empty;
    public string CpuText { get; } = $"{(source.CpuPercent ?? 0):F1}%";
    public string PidText { get; } = source.Pid?.ToString() ?? string.Empty;
}
