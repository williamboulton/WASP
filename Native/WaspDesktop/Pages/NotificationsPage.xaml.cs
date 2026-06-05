using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace WaspDesktop.Pages;

public sealed partial class NotificationsPage : Page
{
    public NotificationsPage()
    {
        InitializeComponent();
        NotificationList.ItemsSource = ((App)Application.Current).NotificationCenter.History;
    }

    private void Clear_Click(object sender, RoutedEventArgs e)
    {
        ((App)Application.Current).NotificationCenter.ClearHistory();
    }
}
