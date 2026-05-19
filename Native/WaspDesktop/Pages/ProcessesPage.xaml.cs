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
    private ProcessSortColumn _sortColumn = ProcessSortColumn.Cpu;
    private bool _sortAscending;

    public ProcessesPage()
    {
        InitializeComponent();
        UpdateSortHeaderLabels();
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

    private void HeaderSort_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string tag })
        {
            return;
        }

        var requested = tag switch
        {
            "name" => ProcessSortColumn.Name,
            "owner" => ProcessSortColumn.Owner,
            "pid" => ProcessSortColumn.Pid,
            "priority" => ProcessSortColumn.Priority,
            "location" => ProcessSortColumn.Location,
            _ => ProcessSortColumn.Cpu
        };

        if (_sortColumn == requested)
        {
            _sortAscending = !_sortAscending;
        }
        else
        {
            _sortColumn = requested;
            _sortAscending = true;
        }

        UpdateSortHeaderLabels();
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

                var target = $"{p.Name} {p.Owner} {p.Pid} {p.Priority} {p.Location}".ToLowerInvariant();
                return target.Contains(search);
            });

        var sorted = SortProcesses(items);
        var projected = sorted
            .Select(p => new ProcessRow(
                p.Name ?? string.Empty,
                p.Owner ?? string.Empty,
                p.Pid?.ToString() ?? string.Empty,
                p.Priority ?? string.Empty,
                $"{(p.CpuPercent ?? 0):F1}%",
                p.Location ?? string.Empty))
            .ToList();

        ProcessList.ItemsSource = projected;
    }

    private IEnumerable<ProcessSnapshot> SortProcesses(IEnumerable<ProcessSnapshot> source)
    {
        IOrderedEnumerable<ProcessSnapshot> ordered = _sortColumn switch
        {
            ProcessSortColumn.Name => _sortAscending
                ? source.OrderBy(p => p.Name ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Name ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            ProcessSortColumn.Owner => _sortAscending
                ? source.OrderBy(p => p.Owner ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Owner ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            ProcessSortColumn.Pid => _sortAscending
                ? source.OrderBy(p => p.Pid ?? 0)
                : source.OrderByDescending(p => p.Pid ?? 0),
            ProcessSortColumn.Priority => _sortAscending
                ? source.OrderBy(p => PriorityRank(p.Priority))
                : source.OrderByDescending(p => PriorityRank(p.Priority)),
            ProcessSortColumn.Location => _sortAscending
                ? source.OrderBy(p => p.Location ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Location ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            _ => _sortAscending
                ? source.OrderBy(p => p.CpuPercent ?? 0)
                : source.OrderByDescending(p => p.CpuPercent ?? 0)
        };

        return ordered;
    }

    private static int PriorityRank(string? priority)
    {
        var value = (priority ?? string.Empty).Trim().ToLowerInvariant().Replace("_", " ");
        return value switch
        {
            "idle" => 0,
            "below normal" => 1,
            "normal" => 2,
            "above normal" => 3,
            "high" => 4,
            "real time" => 5,
            "realtime" => 5,
            _ => int.MaxValue
        };
    }

    private void UpdateSortHeaderLabels()
    {
        SortNameBtn.Content = SortHeaderText("Name", ProcessSortColumn.Name, _sortColumn, _sortAscending);
        SortOwnerBtn.Content = SortHeaderText("Owner", ProcessSortColumn.Owner, _sortColumn, _sortAscending);
        SortPidBtn.Content = SortHeaderText("PID", ProcessSortColumn.Pid, _sortColumn, _sortAscending);
        SortPriorityBtn.Content = SortHeaderText("Priority", ProcessSortColumn.Priority, _sortColumn, _sortAscending);
        SortCpuBtn.Content = SortHeaderText("CPU %", ProcessSortColumn.Cpu, _sortColumn, _sortAscending);
        SortLocationBtn.Content = SortHeaderText("Location", ProcessSortColumn.Location, _sortColumn, _sortAscending);
    }

    private static string SortHeaderText(string label, ProcessSortColumn thisColumn, ProcessSortColumn activeColumn, bool asc)
    {
        if (thisColumn != activeColumn)
        {
            return label;
        }

        return asc ? $"{label} ▲" : $"{label} ▼";
    }
}

public sealed record ProcessRow(string Name, string Owner, string Pid, string Priority, string Cpu, string Location);

