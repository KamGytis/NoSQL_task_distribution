package lt.vu.tracker.util;

public final class TimeFormat {
    private TimeFormat() {}

    // 0 -> "0m", 45 -> "45m", 60 -> "1h", 90 -> "1h 30m".
    public static String minutes(long total) {
        if (total <= 0) {
            return "0m";
        }
        long hours = total / 60;
        long minutes = total % 60;
        if (hours == 0) {
            return minutes + "m";
        }
        if (minutes == 0) {
            return hours + "h";
        }
        return hours + "h " + minutes + "m";
    }
}