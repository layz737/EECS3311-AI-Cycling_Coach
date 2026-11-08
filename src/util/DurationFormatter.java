package util;

import java.util.Locale;

/** Formats stored seconds for display without changing the underlying duration. */
public final class DurationFormatter {
    private DurationFormatter() {
    }

    /** Returns H:MM:SS, rounded to the nearest whole second; hours may exceed 24. */
    public static String format(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds >= Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration must be finite, non-negative, and within the long range.");
        }
        long totalSeconds = Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d:%02d",
                totalSeconds / 3600, totalSeconds % 3600 / 60, totalSeconds % 60);
    }
}
