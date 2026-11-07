package service;

import model.Ride;

import java.util.Locale;

/** Deterministic comparison of two completed rides for Stage 1 feature F09. */
public class RideComparisonService {
    /**
     * Reports both values and the signed difference (ride 2 minus ride 1).
     *
     * @throws IllegalArgumentException if either ride is null or both are the same object
     */
    public String compareRides(Ride ride1, Ride ride2) {
        if (ride1 == null || ride2 == null) {
            throw new IllegalArgumentException("Two non-null rides are required.");
        }
        if (ride1 == ride2) {
            throw new IllegalArgumentException("Select two distinct ride objects.");
        }
        return String.join("\n",
                "Ride 1: " + ride1.getDate() + "; Ride 2: " + ride2.getDate(),
                "Differences are ride 2 minus ride 1.",
                metric("Distance", ride1.getDistance(), ride2.getDistance(), "km"),
                metric("Duration", ride1.getDuration(), ride2.getDuration(), "s"),
                metric("Average speed", ride1.getAvgSpeed(), ride2.getAvgSpeed(), "km/h"),
                metric("Average heart rate", ride1.getAvgHR(), ride2.getAvgHR(), "bpm"),
                metric("Maximum heart rate", ride1.getMaxHR(), ride2.getMaxHR(), "bpm"),
                metric("Average power", ride1.getAvgPower(), ride2.getAvgPower(), "W"),
                metric("Maximum power", ride1.getMaxPower(), ride2.getMaxPower(), "W"),
                metric("Average cadence", ride1.getAvgCadence(), ride2.getAvgCadence(), "rpm"));
    }

    private static String metric(String label, double first, double second, String unit) {
        return String.format(Locale.ROOT, "%s: %.2f -> %.2f %s; difference: %+.2f %s",
                label, first, second, unit, second - first, unit);
    }

    private static String metric(String label, int first, int second, String unit) {
        return String.format(Locale.ROOT, "%s: %d -> %d %s; difference: %+d %s",
                label, first, second, unit, second - first, unit);
    }
}
