package model;

import java.time.LocalDate;

/** A planned workout from Stage 1, with duration in seconds and target power in watts. */
public class Workout {
    private final LocalDate date;
    private final String type;
    private final double duration;
    private final int targetPower;
    private final String description;

    /**
     * Creates a complete workout.
     *
     * @throws IllegalArgumentException if a required value is missing or invalid
     */
    public Workout(LocalDate date, String type, double duration,
                   int targetPower, String description) {
        if (date == null) {
            throw new IllegalArgumentException("date is required.");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required.");
        }
        if (!Double.isFinite(duration) || duration <= 0) {
            throw new IllegalArgumentException("duration must be a finite positive number.");
        }
        if (targetPower < 0) {
            throw new IllegalArgumentException("targetPower must not be negative.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required.");
        }

        this.date = date;
        this.type = type.strip();
        this.duration = duration;
        this.targetPower = targetPower;
        this.description = description.strip();
    }

    public LocalDate getDate() {
        return date;
    }

    public String getType() {
        return type;
    }

    public double getDuration() {
        return duration;
    }

    public int getTargetPower() {
        return targetPower;
    }

    public String getDescription() {
        return description;
    }
}
