package service;

import model.TrainingPlan;
import model.Workout;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Strict PLAN/END decoder. Rest markers are not positive-duration Workout objects. */
public class TrainingPlanParser {
    /** Parses exactly seven consecutive dated rows beginning on Monday. */
    public TrainingPlan parsePlan(String text) {
        return parse(text, null);
    }

    /** Also verifies that every row belongs to the specifically requested week. */
    public TrainingPlan parsePlan(String text, LocalDate weekStart) {
        if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart must be a non-null Monday.");
        }
        return parse(text, weekStart);
    }

    private TrainingPlan parse(String text, LocalDate expectedStart) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Training plan response is missing.");
        }
        // One terminal line ending is allowed; blank lines and commentary are not.
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] lines = normalized.split("\n", -1);
        if (lines.length != 9 || !lines[0].equals("PLAN") || !lines[8].equals("END")) {
            throw new IllegalArgumentException("Expected PLAN, exactly seven daily rows, then END; no commentary.");
        }
        List<Workout> workouts = new ArrayList<>();
        LocalDate monday = expectedStart;
        for (int i = 1; i <= 7; i++) {
            try {
                String[] fields = lines[i].split("\\|", -1);
                if (fields.length != 5) {
                    throw new IllegalArgumentException("Expected five pipe-separated fields.");
                }
                for (int j = 0; j < fields.length; j++) {
                    fields[j] = fields[j].strip();
                    if (fields[j].isEmpty()) {
                        throw new IllegalArgumentException("Field " + (j + 1) + " is blank.");
                    }
                }
                LocalDate date = LocalDate.parse(fields[0]);
                if (monday == null) {
                    monday = date;
                    if (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                        throw new IllegalArgumentException("The week must begin on Monday.");
                    }
                }
                if (!date.equals(monday.plusDays(i - 1))) {
                    throw new IllegalArgumentException("Expected date " + monday.plusDays(i - 1) + ".");
                }
                if (!fields[2].matches("\\d+(?:\\.\\d+)?") || !fields[3].matches("\\d+")) {
                    throw new IllegalArgumentException("Duration must be decimal minutes; target power must be whole watts.");
                }
                double minutes = Double.parseDouble(fields[2]);
                int power = Integer.parseInt(fields[3]);
                if (fields[1].equalsIgnoreCase("Rest")) {
                    if (minutes != 0 || power != 0) {
                        throw new IllegalArgumentException("Rest rows must have zero duration and target power.");
                    }
                } else {
                    workouts.add(new Workout(date, fields[1], minutes * 60, power, fields[4]));
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid training plan line " + (i + 1)
                        + ": " + exception.getMessage(), exception);
            }
        }
        // No TrainingPlan exists until every row has passed validation.
        TrainingPlan plan = new TrainingPlan();
        for (Workout workout : workouts) {
            plan.addWorkout(workout);
        }
        return plan;
    }
}
