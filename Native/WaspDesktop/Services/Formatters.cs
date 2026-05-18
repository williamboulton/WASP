namespace WaspDesktop.Services;

public static class Formatters
{
    public static string Percent(double? value) => value is null ? "-" : $"{value.Value:F1}%";

    public static string ClockSpeed(double? mhz)
    {
        if (mhz is null || mhz <= 0)
        {
            return "-";
        }

        return mhz >= 1000 ? $"{mhz / 1000:F2} GHz" : $"{mhz:F0} MHz";
    }

    public static string GiB(long? bytes)
    {
        if (bytes is null)
        {
            return "-";
        }

        var gib = bytes.Value / (1024d * 1024d * 1024d);
        return $"{gib:F1} GiB";
    }

    public static string Rate(long? bytesPerSec)
    {
        if (bytesPerSec is null)
        {
            return "-";
        }

        var value = bytesPerSec.Value;
        var kb = value / 1024d;
        var mb = kb / 1024d;
        if (mb >= 1)
        {
            return $"{mb:F2} MB/s";
        }

        if (kb >= 1)
        {
            return $"{kb:F2} KB/s";
        }

        return $"{value} B/s";
    }
}
