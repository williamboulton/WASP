using System.Text.Json;
using Windows.Storage;

namespace WaspDesktop.Services;

public sealed class AppSettingsService
{
    private const string RefreshIntervalKey = "refresh_interval_seconds";
    private const string ExtraNotificationsKey = "extra_notifications_enabled";
    private const string SharedSettingsFileName = "app_settings.json";
    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;
    private readonly List<string> _sharedSettingsPaths = [];

    private int _refreshIntervalSeconds = 2;
    private bool _extraNotificationsEnabled;

    public event EventHandler? SettingsChanged;

    public AppSettingsService()
    {
        _refreshIntervalSeconds = Math.Clamp(ReadInt(RefreshIntervalKey, 2), 1, 30);
        _extraNotificationsEnabled = ReadBool(ExtraNotificationsKey, false);
        _sharedSettingsPaths = ResolveSharedSettingsPaths();
        WriteSharedSettingsFile();
    }

    public int RefreshIntervalSeconds
    {
        get => _refreshIntervalSeconds;
        set
        {
            var normalized = Math.Clamp(value, 1, 30);
            if (_refreshIntervalSeconds == normalized)
            {
                return;
            }

            _refreshIntervalSeconds = normalized;
            _localSettings.Values[RefreshIntervalKey] = normalized;
            WriteSharedSettingsFile();
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
            WriteSharedSettingsFile();
            SettingsChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    private List<string> ResolveSharedSettingsPaths()
    {
        var paths = new List<string>();

        var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (!string.IsNullOrWhiteSpace(localAppData))
        {
            var userWaspDir = Path.Combine(localAppData, "WASP");
            Directory.CreateDirectory(userWaspDir);
            paths.Add(Path.Combine(userWaspDir, SharedSettingsFileName));
        }

        var programData = Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData);
        if (!string.IsNullOrWhiteSpace(programData))
        {
            var sharedWaspDir = Path.Combine(programData, "WASP");
            paths.Add(Path.Combine(sharedWaspDir, SharedSettingsFileName));
        }

        if (paths.Count == 0)
        {
            paths.Add(Path.Combine(ApplicationData.Current.LocalFolder.Path, SharedSettingsFileName));
        }

        return paths;
    }

    private void WriteSharedSettingsFile()
    {
        var payload = new Dictionary<string, object>
        {
            ["refresh_interval_seconds"] = _refreshIntervalSeconds,
            ["extra_notifications_enabled"] = _extraNotificationsEnabled
        };

        var json = JsonSerializer.Serialize(payload);
        foreach (var path in _sharedSettingsPaths)
        {
            try
            {
                var dir = Path.GetDirectoryName(path);
                if (!string.IsNullOrWhiteSpace(dir))
                {
                    Directory.CreateDirectory(dir);
                }
                File.WriteAllText(path, json);
            }
            catch
            {
                // Keep settings in-app even if a shared file write fails.
            }
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
