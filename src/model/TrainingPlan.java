package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Collection of planned workouts from Stage 1. Observer support is deferred. */
public class TrainingPlan {
    private final List<Workout> workouts = new ArrayList<>();

    /** Adds a workout in insertion order; multiple workouts on one date are allowed. */
    public void addWorkout(Workout workout) {
        requireWorkout(workout);
        workouts.add(workout);
    }

    /** Removes the first occurrence of the supplied workout, if present. */
    public void removeWorkout(Workout workout) {
        requireWorkout(workout);
        workouts.remove(workout);
    }

    /** Returns an unmodifiable snapshot of the workouts. */
    public List<Workout> getWorkouts() {
        return List.copyOf(workouts);
    }

    /** Removes workouts before today in the system default timezone; retains today's workouts. */
    public void removeExpiredWorkouts() {
        LocalDate today = LocalDate.now();
        workouts.removeIf(workout -> workout.getDate().isBefore(today));
    }

    private static void requireWorkout(Workout workout) {
        if (workout == null) {
            throw new IllegalArgumentException("workout is required.");
        }
    }
}
