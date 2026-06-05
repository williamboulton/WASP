using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using WaspDesktop.Services;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace WaspDesktop.Pages;

public sealed partial class SettingsPage : Page
{
    private readonly AppSettingsService _settings;
    private bool _initializing;

    public SettingsPage()
    {
        InitializeComponent();
        _settings = ((App)Application.Current).Settings;
        InitializeFromSettings();
    }

    private void InitializeFromSettings()
    {
        _initializing = true;
        try
        {
            var targetTag = _settings.RefreshIntervalSeconds.ToString();
            var selected = RefreshIntervalCombo.Items
                .OfType<ComboBoxItem>()
                .FirstOrDefault(item => string.Equals(item.Tag?.ToString(), targetTag, StringComparison.Ordinal));

            RefreshIntervalCombo.SelectedItem = selected ?? RefreshIntervalCombo.Items.OfType<ComboBoxItem>().FirstOrDefault();
            ExtraNotificationsToggle.IsOn = _settings.ExtraNotificationsEnabled;
        }
        finally
        {
            _initializing = false;
        }
    }

    private void RefreshIntervalCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_initializing)
        {
            return;
        }

        if (RefreshIntervalCombo.SelectedItem is ComboBoxItem { Tag: not null } item &&
            int.TryParse(item.Tag.ToString(), out var seconds))
        {
            _settings.RefreshIntervalSeconds = seconds;
        }
    }

    private void ExtraNotificationsToggle_Toggled(object sender, RoutedEventArgs e)
    {
        if (_initializing)
        {
            return;
        }

        _settings.ExtraNotificationsEnabled = ExtraNotificationsToggle.IsOn;
    }
}
