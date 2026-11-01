package model;

import observer.Observer;
import observer.Subject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Collection of planned workouts from Stage 1 that notifies observers after changes. */
public class TrainingPlan implements Subject {
    private final List<Workout> workouts = new ArrayList<>();
    private final List<Observer> observers = new ArrayList<>();

    /** Adds a workout in insertion order; multiple workouts on one date are allowed. */
    public void addWorkout(Workout workout) {
        requireWorkout(workout);
        workouts.add(workout);
        notifyObservers();
    }

    /** Removes the first occurrence of the supplied workout, if present. */
    public void removeWorkout(Workout workout) {
        requireWorkout(workout);
        if (workouts.remove(workout)) {
            notifyObservers();
        }
    }

    /** Returns an unmodifiable snapshot of the workouts. */
    public List<Workout> getWorkouts() {
        return List.copyOf(workouts);
    }

    /** Removes workouts before today in the system default timezone; retains today's workouts. */
    public void removeExpiredWorkouts() {
        LocalDate today = LocalDate.now();
        if (workouts.removeIf(workout -> workout.getDate().isBefore(today))) {
            notifyObservers();
        }
    }

    @Override
    public void addObserver(Observer observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer is required.");
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        for (Observer observer : List.copyOf(observers)) {
            observer.update();
        }
    }

    private static void requireWorkout(Workout workout) {
        if (workout == null) {
            throw new IllegalArgumentException("workout is required.");
        }
    }
}
