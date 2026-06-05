using Microsoft.UI.Dispatching;
using System.Collections.Concurrent;
using System.Collections.ObjectModel;

namespace WaspDesktop.Services;

public enum NotificationSeverity
{
    Info,
    Success,
    Warning,
    Error
}

public sealed class AppNotification
{
    public Guid Id { get; } = Guid.NewGuid();
    public DateTimeOffset Timestamp { get; } = DateTimeOffset.Now;
    public string TimeText => Timestamp.ToString("HH:mm:ss");
    public NotificationSeverity Severity { get; }
    public string SeverityText => $"[{Severity.ToString().ToUpperInvariant()}]";
    public string Title { get; }
    public string Message { get; }

    public AppNotification(NotificationSeverity severity, string title, string message)
    {
        Severity = severity;
        Title = title;
        Message = message;
    }
}

public sealed class NotificationCenter
{
    private readonly ConcurrentQueue<Action> _pendingUiActions = new();
    private readonly TimeSpan _toastDuration = TimeSpan.FromSeconds(4);
    private DispatcherQueue? _dispatcherQueue;

    public ObservableCollection<AppNotification> History { get; } = [];
    public ObservableCollection<AppNotification> ActiveToasts { get; } = [];

    public void InitializeDispatcher(DispatcherQueue dispatcherQueue)
    {
        _dispatcherQueue = dispatcherQueue;
        while (_pendingUiActions.TryDequeue(out var action))
        {
            _dispatcherQueue.TryEnqueue(() => action());
        }
    }

    public void Notify(NotificationSeverity severity, string title, string message, bool showToast = true)
    {
        var notification = new AppNotification(severity, title, message);
        RunOnUi(() =>
        {
            History.Insert(0, notification);
            if (History.Count > 500)
            {
                History.RemoveAt(History.Count - 1);
            }

            if (!showToast)
            {
                return;
            }

            ActiveToasts.Add(notification);
            _ = RemoveToastLaterAsync(notification);
        });
    }

    public void ClearHistory()
    {
        RunOnUi(() => History.Clear());
    }

    private async Task RemoveToastLaterAsync(AppNotification notification)
    {
        await Task.Delay(_toastDuration);
        RunOnUi(() =>
        {
            var existing = ActiveToasts.FirstOrDefault(x => x.Id == notification.Id);
            if (existing is not null)
            {
                ActiveToasts.Remove(existing);
            }
        });
    }

    private void RunOnUi(Action action)
    {
        if (_dispatcherQueue is null)
        {
            _pendingUiActions.Enqueue(action);
            return;
        }

        _dispatcherQueue.TryEnqueue(() => action());
    }
}
