using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using WaspDesktop.Models;
using WaspDesktop.Services;

namespace WaspDesktop.Pages;

public sealed partial class ProcessesPage : Page
{
    private MetricsState? _metricsState;
    private IReadOnlyList<ProcessSnapshot> _allProcesses = [];

    public ProcessesPage()
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

    private void SearchBox_OnTextChanged(object sender, TextChangedEventArgs e)
    {
        ApplyFilter();
    }

    private void Render()
    {
        _allProcesses = _metricsState?.LatestSnapshot?.Processes ?? [];
        ApplyFilter();
    }

    private void ApplyFilter()
    {
        var search = SearchBox.Text?.Trim().ToLowerInvariant() ?? string.Empty;

        var items = _allProcesses
            .Where(p =>
            {
                if (string.IsNullOrWhiteSpace(search))
                {
                    return true;
                }

                var target = $"{p.Name} {p.Owner} {p.Pid} {p.Location}".ToLowerInvariant();
                return target.Contains(search);
            })
            .OrderByDescending(p => p.CpuPercent ?? 0)
            .Select(p => new ProcessRow(
                p.Name ?? string.Empty,
                p.Owner ?? string.Empty,
                p.Pid?.ToString() ?? string.Empty,
                $"{(p.CpuPercent ?? 0):F1}%",
                p.Location ?? string.Empty))
            .ToList();

        ProcessList.ItemsSource = items;
    }
}

public sealed record ProcessRow(string Name, string Owner, string Pid, string Cpu, string Location);
