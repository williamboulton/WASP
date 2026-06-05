using Windows.Storage;

namespace WaspDesktop.Services;

public sealed class AppSettingsService
{
    private const string RefreshIntervalKey = "refresh_interval_seconds";
    private const string ExtraNotificationsKey = "extra_notifications_enabled";
    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;

    private int _refreshIntervalSeconds = 2;
    private bool _extraNotificationsEnabled;

    public event EventHandler? SettingsChanged;

    public AppSettingsService()
    {
        _refreshIntervalSeconds = ReadInt(RefreshIntervalKey, 2);
        _extraNotificationsEnabled = ReadBool(ExtraNotificationsKey, false);
    }

    public int RefreshIntervalSeconds
    {
        get => _refreshIntervalSeconds;
        set
        {
            var normalized = Math.Clamp(value, 1, 10);
            if (_refreshIntervalSeconds == normalized)
            {
                return;
            }

            _refreshIntervalSeconds = normalized;
            _localSettings.Values[RefreshIntervalKey] = normalized;
            SettingsChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    public bool ExtraNotificationsEnabled
    {
        get => _extraNotificationsEnabled;
        set
        {
            if (_extraNotificationsEnabled == value)
            {
                return;
            }

            _extraNotificationsEnabled = value;
            _localSettings.Values[ExtraNotificationsKey] = value;
            SettingsChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    private int ReadInt(string key, int fallback)
    {
        if (_localSettings.Values.TryGetValue(key, out var value) && value is int intValue)
        {
            return intValue;
        }

        return fallback;
    }

    private bool ReadBool(string key, bool fallback)
    {
        if (_localSettings.Values.TryGetValue(key, out var value) && value is bool boolValue)
        {
            return boolValue;
        }

        return fallback;
    }
}
