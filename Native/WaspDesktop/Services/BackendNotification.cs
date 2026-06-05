namespace WaspDesktop.Services;

public sealed class BackendNotification
{
    public string Severity { get; init; } = "info";
    public string Category { get; init; } = "general";
    public string Title { get; init; } = "Backend";
    public string Message { get; init; } = string.Empty;
}
