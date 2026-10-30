package model;

import java.time.LocalDate;

/**
 * Summary of one completed ride in the Stage 1 design.
 * Distance is in kilometres, duration in seconds, speed in kilometres per hour,
 * heart rate in beats per minute, power in watts, and cadence in revolutions per minute.
 */
public class Ride {
    private final LocalDate date;
    private final double distance;
    private final double duration;
    private final double avgSpeed;
    private final int avgHR;
    private final int maxHR;
    private final int avgPower;
    private final int maxPower;
    private final int avgCadence;

    /**
     * Creates a validated ride summary. Zero metrics are allowed, for example
     * distance on an indoor ride or power while coasting; they do not mean missing data.
     *
     * @throws IllegalArgumentException if the date is missing, a metric is invalid,
     *                                  or an average exceeds its maximum
     */
    public Ride(LocalDate date, double distance, double duration, double avgSpeed,
                int avgHR, int maxHR, int avgPower, int maxPower, int avgCadence) {
        if (date == null) {
            throw new IllegalArgumentException("date is required.");
        }
        requireNonNegativeFinite(distance, "distance");
        if (!Double.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("duration must be a finite positive number.");
        }
        requireNonNegativeFinite(avgSpeed, "avgSpeed");
        requireNonNegative(avgHR, "avgHR");
        requireNonNegative(maxHR, "maxHR");
        requireNonNegative(avgPower, "avgPower");
        requireNonNegative(maxPower, "maxPower");
        requireNonNegative(avgCadence, "avgCadence");
        if (avgHR > maxHR) {
            throw new IllegalArgumentException("avgHR must not exceed maxHR.");
        }
        if (avgPower > maxPower) {
            throw new IllegalArgumentException("avgPower must not exceed maxPower.");
        }

        this.date = date;
        this.distance = distance;
        this.duration = duration;
        this.avgSpeed = avgSpeed;
        this.avgHR = avgHR;
        this.maxHR = maxHR;
        this.avgPower = avgPower;
        this.maxPower = maxPower;
        this.avgCadence = avgCadence;
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(field + " must be a finite non-negative number.");
        }
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
    }

    public LocalDate getDate() {
        return date;
    }

    public double getDistance() {
        return distance;
    }

    public double getDuration() {
        return duration;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public int getAvgHR() {
        return avgHR;
    }

    public int getMaxHR() {
        return maxHR;
    }

    public int getAvgPower() {
        return avgPower;
    }

    public int getMaxPower() {
        return maxPower;
    }

    public int getAvgCadence() {
        return avgCadence;
    }
}
