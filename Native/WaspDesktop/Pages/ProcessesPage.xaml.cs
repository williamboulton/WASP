using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using System.Collections.ObjectModel;
using WaspDesktop.Models;
using WaspDesktop.Services;

namespace WaspDesktop.Pages;

public sealed partial class ProcessesPage : Page
{
    private MetricsState? _metricsState;
    private IReadOnlyList<ProcessSnapshot> _allProcesses = [];
    private readonly ObservableCollection<ProcessRow> _rows = [];
    private ProcessSortColumn _sortColumn = ProcessSortColumn.Cpu;
    private bool _sortAscending;

    public ProcessesPage()
    {
        InitializeComponent();
        ProcessList.ItemsSource = _rows;
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
            .ToList();
        ReconcileByIndex(
            _rows,
            projected,
            p => new ProcessRow(p),
            (row, p) => row.UpdateFrom(p));
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

    private static void ReconcileByIndex<TItem, TSource>(
        ObservableCollection<TItem> target,
        IReadOnlyList<TSource> desired,
        Func<TSource, TItem> createItem,
        Action<TItem, TSource> updateItem)
    {
        var overlap = Math.Min(target.Count, desired.Count);
        for (var index = 0; index < overlap; index++)
        {
            updateItem(target[index], desired[index]);
        }

        for (var index = overlap; index < desired.Count; index++)
        {
            target.Add(createItem(desired[index]));
        }

        while (target.Count > desired.Count)
        {
            target.RemoveAt(target.Count - 1);
        }
    }
}

public sealed class ProcessRow : ObservableEntity
{
    private string _name = string.Empty;
    private string _owner = string.Empty;
    private string _pid = string.Empty;
    private string _priority = string.Empty;
    private string _cpu = string.Empty;
    private string _location = string.Empty;

    public string Key { get; private set; } = string.Empty;
    public string Name { get => _name; private set => SetProperty(ref _name, value); }
    public string Owner { get => _owner; private set => SetProperty(ref _owner, value); }
    public string Pid { get => _pid; private set => SetProperty(ref _pid, value); }
    public string Priority { get => _priority; private set => SetProperty(ref _priority, value); }
    public string Cpu { get => _cpu; private set => SetProperty(ref _cpu, value); }
    public string Location { get => _location; private set => SetProperty(ref _location, value); }

    public ProcessRow(ProcessSnapshot source)
    {
        UpdateFrom(source);
    }

    public static string BuildKey(ProcessSnapshot source) => $"{source.Pid?.ToString() ?? "0"}|{source.Name ?? string.Empty}";

    public void UpdateFrom(ProcessSnapshot source)
    {
        Key = BuildKey(source);
        Name = source.Name ?? string.Empty;
        Owner = source.Owner ?? string.Empty;
        Pid = source.Pid?.ToString() ?? string.Empty;
        Priority = source.Priority ?? string.Empty;
        Cpu = $"{(source.CpuPercent ?? 0):F1}%";
        Location = source.Location ?? string.Empty;
    }
}

