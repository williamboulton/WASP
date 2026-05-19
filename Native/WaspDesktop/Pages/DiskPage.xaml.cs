using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using System.Collections.ObjectModel;
using WaspDesktop.Models;
using WaspDesktop.Services;

namespace WaspDesktop.Pages;

public sealed partial class DiskPage : Page
{
    private MetricsState? _metricsState;
    private readonly ObservableCollection<DiskCardItem> _diskItems = [];

    public DiskPage()
    {
        InitializeComponent();
        DiskList.ItemsSource = _diskItems;
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
            .ToList();

        ReconcileByKey(
            _diskItems,
            diskItems,
            item => item.Key,
            snapshot => DiskCardItem.BuildKey(snapshot),
            snapshot => new DiskCardItem(snapshot),
            (item, snapshot) => item.UpdateFrom(snapshot));
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
}

public sealed class DiskCardItem : ObservableEntity
{
    private string _name = string.Empty;
    private double _usedPercent;
    private string _usedPercentText = string.Empty;
    private string _capacity = string.Empty;

    public string Key { get; private set; } = string.Empty;
    public string Name { get => _name; private set => SetProperty(ref _name, value); }
    public double UsedPercent { get => _usedPercent; private set => SetProperty(ref _usedPercent, value); }
    public string UsedPercentText { get => _usedPercentText; private set => SetProperty(ref _usedPercentText, value); }
    public string Capacity { get => _capacity; private set => SetProperty(ref _capacity, value); }
    public ObservableCollection<DetailRow> Details { get; } = [];

    public DiskCardItem(DiskSnapshot snapshot)
    {
        EnsureDetailRows();
        UpdateFrom(snapshot);
    }

    public static string BuildKey(DiskSnapshot snapshot)
    {
        return FormatDrive(snapshot.DriveLetter);
    }

    public void UpdateFrom(DiskSnapshot snapshot)
    {
        var total = snapshot.TotalBytes ?? 0;
        var free = snapshot.FreeBytes ?? 0;
        var used = Math.Max(0, total - free);
        var usedPercent = total > 0 ? Math.Clamp((used * 100d) / total, 0, 100) : 0;

        var driveLabel = FormatDrive(snapshot.DriveLetter);
        Key = driveLabel;
        Name = driveLabel;
        UsedPercent = usedPercent;
        UsedPercentText = $"{usedPercent:F1}% used";
        Capacity = $"{Formatters.GiB(used)} / {Formatters.GiB(snapshot.TotalBytes)}";

        Details[0].Update("Drive Letter", driveLabel);
        Details[1].Update("Total Bytes", $"{Formatters.Integer(snapshot.TotalBytes)} ({Formatters.GiB(snapshot.TotalBytes)})");
        Details[2].Update("Free Bytes", $"{Formatters.Integer(snapshot.FreeBytes)} ({Formatters.GiB(snapshot.FreeBytes)})");
        Details[3].Update("Read Speed Bytes Per Sec", $"{Formatters.Integer(snapshot.ReadBytesPerSec)} ({Formatters.Rate(snapshot.ReadBytesPerSec)})");
        Details[4].Update("Write Speed Bytes Per Sec", $"{Formatters.Integer(snapshot.WriteBytesPerSec)} ({Formatters.Rate(snapshot.WriteBytesPerSec)})");
    }

    private void EnsureDetailRows()
    {
        while (Details.Count < 5)
        {
            Details.Add(new DetailRow("-", "-"));
        }
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

public sealed class DetailRow : ObservableEntity
{
    private string _label;
    private string _value;

    public string Label { get => _label; private set => SetProperty(ref _label, value); }
    public string Value { get => _value; private set => SetProperty(ref _value, value); }

    public DetailRow(string label, string value)
    {
        _label = label;
        _value = value;
    }

    public void Update(string label, string value)
    {
        Label = label;
        Value = value;
    }
}
