using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Microsoft.UI.Xaml.Navigation;
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
        UpdateHistory(snapshot);

        var coreItems = (snapshot?.CpuCores ?? [])
            .Where(c => c.CoreIndex is not null)
            .OrderBy(c => c.CoreIndex)
            .Select(c => new CoreGaugeItem(c))
            .ToList();
        CoreGrid.ItemsSource = coreItems;

        var items = (snapshot?.Processes ?? [])
            .Where(p => (p.CpuPercent ?? 0) > 0)
            .OrderByDescending(p => p.CpuPercent ?? 0)
            .Take(20)
            .Select(p => new ProcessListItem(p))
            .ToList();
        ProcessList.ItemsSource = items;
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
}

public sealed class ProcessListItem(ProcessSnapshot source)
{
    public string Name { get; } = source.Name ?? string.Empty;
    public string Owner { get; } = source.Owner ?? string.Empty;
    public string CpuText { get; } = $"{(source.CpuPercent ?? 0):F1}%";
    public string PidText { get; } = source.Pid?.ToString() ?? string.Empty;
}

public sealed class CoreGaugeItem
{
    public string Label { get; }
    public string UsageText { get; }
    public string SliceGeometry { get; }

    public CoreGaugeItem(CpuCoreSnapshot source)
    {
        var usage = Math.Clamp(source.CoreUsagePercent ?? 0, 0, 100);
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
