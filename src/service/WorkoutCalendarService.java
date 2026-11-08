package service;

import model.Ride;
import model.RideHistory;
import model.TrainingPlan;
import model.Workout;
import observer.Observer;
import util.DurationFormatter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Stage 1 F10 calendar queries combining planned workouts and completed rides. */
public class WorkoutCalendarService implements Observer {
    private final TrainingPlan trainingPlan;
    private final RideHistory rideHistory;

    /**
     * A null plan represents no training plan. Ride history must be non-null,
     * but may be empty. Observer registration is performed by the caller.
     */
    public WorkoutCalendarService(TrainingPlan trainingPlan, RideHistory rideHistory) {
        if (rideHistory == null) {
            throw new IllegalArgumentException("rideHistory is required.");
        }
        this.trainingPlan = trainingPlan;
        this.rideHistory = rideHistory;
    }

    /** Returns an unmodifiable, date-ordered snapshot for the containing Monday-Sunday week. */
    public List<Workout> getWeekWorkouts(LocalDate date) {
        LocalDate monday = weekStart(date);
        LocalDate sunday = monday.plusDays(6);
        if (trainingPlan == null) {
            return List.of();
        }
        return trainingPlan.getWorkouts().stream()
                .filter(workout -> inWeek(workout.getDate(), monday, sunday))
                .sorted(Comparator.comparing(Workout::getDate))
                .toList();
    }

    /** Returns an unmodifiable, date-ordered snapshot for the containing Monday-Sunday week. */
    public List<Ride> getWeekRides(LocalDate date) {
        LocalDate monday = weekStart(date);
        LocalDate sunday = monday.plusDays(6);
        return rideHistory.getRides().stream()
                .filter(ride -> inWeek(ride.getDate(), monday, sunday))
                .sorted(Comparator.comparing(Ride::getDate))
                .toList();
    }

    /**
     * Lists all seven dates, with planned workouts followed by completed rides
     * on each day. Entries with the same date retain source insertion order.
     */
    public String getWorkoutCalendar(LocalDate date) {
        LocalDate monday = weekStart(date);
        List<Workout> workouts = getWeekWorkouts(date);
        List<Ride> rides = getWeekRides(date);
        StringBuilder calendar = new StringBuilder("Workout calendar: ")
                .append(monday).append(" to ").append(monday.plusDays(6));
        for (int offset = 0; offset < 7; offset++) {
            LocalDate day = monday.plusDays(offset);
            calendar.append('\n').append(day).append(" (").append(day.getDayOfWeek()).append(')');
            boolean hasEntries = false;
            for (Workout workout : workouts) {
                if (workout.getDate().equals(day)) {
                    calendar.append(String.format(Locale.ROOT,
                            "\n  Planned workout: %s; duration: %s; target power: %d W; %s",
                            workout.getType(), DurationFormatter.format(workout.getDuration()), workout.getTargetPower(),
                            workout.getDescription()));
                    hasEntries = true;
                }
            }
            for (Ride ride : rides) {
                if (ride.getDate().equals(day)) {
                    calendar.append(String.format(Locale.ROOT,
                            "\n  Completed ride: distance: %.2f km; duration: %s; average speed: %.2f km/h",
                            ride.getDistance(), DurationFormatter.format(ride.getDuration()), ride.getAvgSpeed()));
                    hasEntries = true;
                }
            }
            if (!hasEntries) {
                calendar.append("\n  No planned workouts or completed rides.");
            }
        }
        return calendar.toString();
    }

    /** No cache to refresh: each query reads the current plan and ride history. */
    @Override
    public void update() {
        // Intentionally empty; on-demand queries already reflect all model changes.
    }

    private static LocalDate weekStart(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date is required.");
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static boolean inWeek(LocalDate date, LocalDate monday, LocalDate sunday) {
        return !date.isBefore(monday) && !date.isAfter(sunday);
    }
}
