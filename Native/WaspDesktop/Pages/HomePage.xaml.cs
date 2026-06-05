using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Microsoft.UI.Xaml.Navigation;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using WaspDesktop.Models;
using WaspDesktop.Services;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace WaspDesktop.Pages;

public sealed partial class HomePage : Page
{
    private const int HistoryMaxPoints = 30;
    private MetricsState? _metricsState;
    private MetricsSnapshot? _lastHistorySnapshot;
    private readonly Queue<double> _cpuHistory = new();
    private readonly Queue<double> _memoryHistory = new();
    private readonly ObservableCollection<CoreGaugeItem> _coreItems = [];
    private readonly ObservableCollection<ProcessListItem> _processItems = [];
    private ProcessSortColumn _mainSortColumn = ProcessSortColumn.Cpu;
    private bool _mainSortAscending;

    public HomePage()
    {
        InitializeComponent();
        CoreGrid.ItemsSource = _coreItems;
        ProcessList.ItemsSource = _processItems;
        UpdateMainSortHeaderLabels();
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
        CpuUsageText.Text = Formatters.Percent(snapshot?.Cpu?.CpuUsagePercent);
        CpuClockText.Text = Formatters.ClockSpeed(snapshot?.Cpu?.CpuMhz);
        ResponsivenessText.Text = Formatters.Percent(snapshot?.Cpu?.SystemResponsivenessPercent);

        var memoryPercent = Formatters.Percent(snapshot?.Memory?.MemoryUsagePercent);
        var used = Formatters.GiB(snapshot?.Memory?.UsedBytes);
        var total = Formatters.GiB(snapshot?.Memory?.TotalBytes);
        MemoryText.Text = $"{memoryPercent} ({used} / {total})";
        UpdateHistory(snapshot);

        var coreItems = (snapshot?.CpuCores ?? [])
            .Where(c => c.CoreIndex is not null)
            .OrderBy(c => c.CoreIndex)
            .ToList();
        ReconcileByKey(
            _coreItems,
            coreItems,
            item => item.Key,
            source => source.CoreIndex ?? -1,
            source => new CoreGaugeItem(source),
            (item, source) => item.UpdateFrom(source));

        var sortedProcesses = SortMainProcesses((snapshot?.Processes ?? [])
            .Where(p => (p.CpuPercent ?? 0) > 0));

        var items = sortedProcesses
            .Take(20)
            .ToList();
        ReconcileByIndex(
            _processItems,
            items,
            source => new ProcessListItem(source),
            (item, source) => item.UpdateFrom(source));
    }

    private IEnumerable<ProcessSnapshot> SortMainProcesses(IEnumerable<ProcessSnapshot> source)
    {
        IOrderedEnumerable<ProcessSnapshot> ordered = _mainSortColumn switch
        {
            ProcessSortColumn.Name => _mainSortAscending
                ? source.OrderBy(p => p.Name ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Name ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            ProcessSortColumn.Owner => _mainSortAscending
                ? source.OrderBy(p => p.Owner ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Owner ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            ProcessSortColumn.Pid => _mainSortAscending
                ? source.OrderBy(p => p.Pid ?? 0)
                : source.OrderByDescending(p => p.Pid ?? 0),
            ProcessSortColumn.Priority => _mainSortAscending
                ? source.OrderBy(p => PriorityRank(p.Priority))
                : source.OrderByDescending(p => PriorityRank(p.Priority)),
            ProcessSortColumn.Location => _mainSortAscending
                ? source.OrderBy(p => p.Location ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                : source.OrderByDescending(p => p.Location ?? string.Empty, StringComparer.OrdinalIgnoreCase),
            _ => _mainSortAscending
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

    private void MainProcessHeaderSort_Click(object sender, RoutedEventArgs e)
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

        if (_mainSortColumn == requested)
        {
            _mainSortAscending = !_mainSortAscending;
        }
        else
        {
            _mainSortColumn = requested;
            _mainSortAscending = true;
        }

        UpdateMainSortHeaderLabels();
        Render();
    }

    private void UpdateMainSortHeaderLabels()
    {
        MainSortNameBtn.Content = SortHeaderText("Name", ProcessSortColumn.Name, _mainSortColumn, _mainSortAscending);
        MainSortOwnerBtn.Content = SortHeaderText("Owner", ProcessSortColumn.Owner, _mainSortColumn, _mainSortAscending);
        MainSortPidBtn.Content = SortHeaderText("PID", ProcessSortColumn.Pid, _mainSortColumn, _mainSortAscending);
        MainSortPriorityBtn.Content = SortHeaderText("Priority", ProcessSortColumn.Priority, _mainSortColumn, _mainSortAscending);
        MainSortCpuBtn.Content = SortHeaderText("CPU %", ProcessSortColumn.Cpu, _mainSortColumn, _mainSortAscending);
        MainSortLocationBtn.Content = SortHeaderText("Location", ProcessSortColumn.Location, _mainSortColumn, _mainSortAscending);
    }

    private static string SortHeaderText(string label, ProcessSortColumn thisColumn, ProcessSortColumn activeColumn, bool asc)
    {
        if (thisColumn != activeColumn)
        {
            return label;
        }

        return asc ? $"{label} ▲" : $"{label} ▼";
    }

    private void UpdateHistory(MetricsSnapshot? snapshot)
    {
        if (snapshot is null || ReferenceEquals(snapshot, _lastHistorySnapshot))
        {
            DrawHistoryChart();
            return;
        }

        _lastHistorySnapshot = snapshot;
        EnqueueBounded(_cpuHistory, snapshot.Cpu?.CpuUsagePercent ?? 0);
        EnqueueBounded(_memoryHistory, snapshot.Memory?.MemoryUsagePercent ?? 0);
        DrawHistoryChart();
    }

    private static void EnqueueBounded(Queue<double> queue, double value)
    {
        queue.Enqueue(Math.Clamp(value, 0, 100));
        while (queue.Count > HistoryMaxPoints)
        {
            queue.Dequeue();
        }
    }

    private void HistoryCanvas_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        DrawHistoryChart();
    }

    private void DrawHistoryChart()
    {
        if (HistoryCanvas is null)
        {
            return;
        }

        var width = HistoryCanvas.ActualWidth;
        var height = HistoryCanvas.ActualHeight;
        if (width <= 0 || height <= 0)
        {
            return;
        }

        HistoryCanvas.Children.Clear();

        for (var i = 1; i <= 4; i++)
        {
            var y = (height / 4) * i;
            HistoryCanvas.Children.Add(new Line
            {
                X1 = 0,
                Y1 = y,
                X2 = width,
                Y2 = y,
                Stroke = new SolidColorBrush(Microsoft.UI.ColorHelper.FromArgb(40, 148, 163, 184)),
                StrokeThickness = 1
            });
        }

        DrawSeries(_cpuHistory.ToList(), width, height, Microsoft.UI.ColorHelper.FromArgb(255, 245, 158, 11));
        DrawSeries(_memoryHistory.ToList(), width, height, Microsoft.UI.ColorHelper.FromArgb(255, 96, 165, 250));
    }

    private void DrawSeries(IReadOnlyList<double> values, double width, double height, Windows.UI.Color color)
    {
        if (values.Count < 2)
        {
            return;
        }

        var polyline = new Polyline
        {
            Stroke = new SolidColorBrush(color),
            StrokeThickness = 2
        };

        var count = values.Count;
        for (var i = 0; i < count; i++)
        {
            var x = count == 1 ? 0 : (width / (count - 1)) * i;
            var y = height - ((values[i] / 100d) * height);
            polyline.Points.Add(new Windows.Foundation.Point(x, y));
        }

        HistoryCanvas.Children.Add(polyline);
    }

    private static void ReconcileByKey<TItem, TSource, TKey>(
        ObservableCollection<TItem> target,
        IReadOnlyList<TSource> desired,
        Func<TItem, TKey> getItemKey,
        Func<TSource, TKey> getSourceKey,
        Func<TSource, TItem> createItem,
        Action<TItem, TSource> updateItem) where TKey : notnull
    {
        for (var index = 0; index < desired.Count; index++)
        {
            var source = desired[index];
            var desiredKey = getSourceKey(source);

            if (index < target.Count && EqualityComparer<TKey>.Default.Equals(getItemKey(target[index]), desiredKey))
            {
                updateItem(target[index], source);
                continue;
            }

            var existingIndex = -1;
            for (var probe = index + 1; probe < target.Count; probe++)
            {
                if (EqualityComparer<TKey>.Default.Equals(getItemKey(target[probe]), desiredKey))
                {
                    existingIndex = probe;
                    break;
                }
            }

            if (existingIndex >= 0)
            {
                target.Move(existingIndex, index);
                updateItem(target[index], source);
            }
            else
            {
                target.Insert(index, createItem(source));
            }
        }

        while (target.Count > desired.Count)
        {
            target.RemoveAt(target.Count - 1);
        }
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

public sealed class ProcessListItem : ObservableEntity
{
    private string _name = string.Empty;
    private string _owner = string.Empty;
    private string _cpuText = string.Empty;
    private string _pidText = string.Empty;
    private string _priority = string.Empty;
    private string _location = string.Empty;

    public string Key { get; private set; } = string.Empty;
    public string Name { get => _name; private set => SetProperty(ref _name, value); }
    public string Owner { get => _owner; private set => SetProperty(ref _owner, value); }
    public string CpuText { get => _cpuText; private set => SetProperty(ref _cpuText, value); }
    public string PidText { get => _pidText; private set => SetProperty(ref _pidText, value); }
    public string Priority { get => _priority; private set => SetProperty(ref _priority, value); }
    public string Location { get => _location; private set => SetProperty(ref _location, value); }

    public ProcessListItem(ProcessSnapshot source)
    {
        UpdateFrom(source);
    }

    public static string BuildKey(ProcessSnapshot source) => $"{source.Pid?.ToString() ?? "0"}|{source.Name ?? string.Empty}";

    public void UpdateFrom(ProcessSnapshot source)
    {
        Key = BuildKey(source);
        Name = source.Name ?? string.Empty;
        Owner = source.Owner ?? string.Empty;
        CpuText = $"{(source.CpuPercent ?? 0):F1}%";
        PidText = source.Pid?.ToString() ?? string.Empty;
        Priority = source.Priority ?? string.Empty;
        Location = source.Location ?? string.Empty;
    }
}


public sealed class CoreGaugeItem : ObservableEntity
{
    private string _label = string.Empty;
    private string _usageText = string.Empty;
    private string _sliceGeometry = string.Empty;
    public int Key { get; private set; }
    public string Label { get => _label; private set => SetProperty(ref _label, value); }
    public string UsageText { get => _usageText; private set => SetProperty(ref _usageText, value); }
    public string SliceGeometry { get => _sliceGeometry; private set => SetProperty(ref _sliceGeometry, value); }

    public CoreGaugeItem(CpuCoreSnapshot source)
    {
        UpdateFrom(source);
    }

    public void UpdateFrom(CpuCoreSnapshot source)
    {
        var usage = Math.Clamp(source.CoreUsagePercent ?? 0, 0, 100);
        Key = source.CoreIndex ?? -1;
        Label = $"CPU {source.CoreIndex ?? 0}";
        UsageText = $"{usage:F0}%";
        SliceGeometry = BuildPieSliceGeometry(usage);
    }

    private static string BuildPieSliceGeometry(double usagePercent)
    {
        const double cx = 35;
        const double cy = 35;
        const double r = 33;

        if (usagePercent <= 0.01)
        {
            return string.Empty;
        }

        if (usagePercent >= 99.99)
        {
            return $"M {cx - r:F2},{cy:F2} A {r:F2},{r:F2} 0 1,0 {cx + r:F2},{cy:F2} A {r:F2},{r:F2} 0 1,0 {cx - r:F2},{cy:F2}";
        }

        var angle = (usagePercent / 100d) * 360d;
        var endRadians = (Math.PI / 180d) * (angle - 90d);
        var endX = cx + (r * Math.Cos(endRadians));
        var endY = cy + (r * Math.Sin(endRadians));
        var largeArcFlag = usagePercent > 50 ? 1 : 0;

        return $"M {cx:F2},{cy:F2} L {cx:F2},{cy - r:F2} A {r:F2},{r:F2} 0 {largeArcFlag},1 {endX:F2},{endY:F2} Z";
    }
}

public abstract class ObservableEntity : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected bool SetProperty<T>(ref T storage, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(storage, value))
        {
            return false;
        }

        storage = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        return true;
    }
}
