package ui;

import controller.CyclingCoachController;
import model.Ride;
import model.TrainingPlan;
import model.Workout;
import util.DurationFormatter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Scanner;

/** Stage 1 command-line view. Application behavior is accessed only through the controller. */
public class CyclingCoachCLI {
    private final CyclingCoachController controller;
    private final Scanner input;
    private final PrintStream output;

    public CyclingCoachCLI(CyclingCoachController controller) {
        this(controller, new Scanner(System.in, StandardCharsets.UTF_8), System.out);
    }

    /** Stream injection supports scripted interaction without closing caller-owned streams. */
    public CyclingCoachCLI(CyclingCoachController controller, Scanner input, PrintStream output) {
        this.controller = Objects.requireNonNull(controller, "controller is required");
        this.input = Objects.requireNonNull(input, "input is required");
        this.output = Objects.requireNonNull(output, "output is required");
    }

    public static void main(String[] args) {
        new CyclingCoachCLI(new CyclingCoachController()).start();
    }

    public void start() {
        if (controller.getProfile().isEmpty()) {
            try {
                output.println(controller.loadProfile() ? "Saved rider profile loaded."
                        : "No saved profile found. Use option 1 to set up your profile.");
            } catch (IOException exception) {
                output.println("Could not load profile: " + exception.getMessage());
            }
        }
        while (!Thread.currentThread().isInterrupted()) {
            output.print("""

                    AI Cycling Training Coach
                    1. Rider Profile Setup / Update
                    2. Import Ride History CSV
                    3. AI Ride Analysis
                    4. Generate Weekly Training Plan
                    5. AI Chat
                    6. Progress Tracking
                    7. Weakness / Improvement Analysis
                    8. Weekly Training Summary
                    9. Ride Comparison
                    10. Workout Calendar
                    11. Personal Bests
                    0. Exit
                    """);
            try {
                int option = integer("Choose an option: ");
                switch (option) {
                    case 0 -> {
                        output.println("Goodbye.");
                        return;
                    }
                    case 1 -> editProfile();
                    case 2 -> importRides();
                    case 3 -> analyzeRide();
                    case 4 -> displayPlan(controller.generateTrainingPlan());
                    case 5 -> output.println(controller.askAI(read("Training question: ")));
                    case 6 -> output.println(controller.getProgress());
                    case 7 -> output.println(controller.analyzeWeaknesses());
                    case 8 -> output.println(controller.getWeeklySummary());
                    case 9 -> compareRides();
                    case 10 -> displayCalendar();
                    case 11 -> output.println(controller.getPersonalBests());
                    default -> output.println("Invalid menu option. Enter a number from 0 to 11.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                output.println("Operation interrupted. Exiting the CLI.");
                return;
            } catch (IOException exception) {
                output.println("Operation failed: " + exception.getMessage());
            } catch (IllegalArgumentException | IllegalStateException | DateTimeException exception) {
                output.println("Unable to complete selection: " + exception.getMessage());
            } catch (NoSuchElementException exception) {
                output.println("Input ended. Exiting the CLI.");
                return;
            }
        }
    }

    private void editProfile() throws IOException {
        double weight = decimal("Weight (kg): ");
        int ftp = integer("FTP (W): ");
        int maxHR = integer("Maximum heart rate (bpm): ");
        int restingHR = integer("Resting heart rate (bpm): ");
        int days = integer("Available training days per week: ");
        double targetWeight = decimal("Target weight (kg): ");
        int targetFTP = integer("Target FTP (W): ");
        String goal = read("Main goal: ");
        if (controller.getProfile().isPresent()) {
            controller.updateProfile(weight, ftp, maxHR, restingHR, days, targetWeight, targetFTP, goal);
        } else {
            controller.setUpProfile(weight, ftp, maxHR, restingHR, days, targetWeight, targetFTP, goal);
        }
        output.println("Rider profile saved.");
    }

    private void importRides() throws IOException {
        String path = read("Garmin activities CSV path: ");
        if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2) {
            path = path.substring(1, path.length() - 1);
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("A CSV file path is required.");
        }
        output.println("Imported " + controller.importRides(Path.of(path)).size() + " rides.");
    }

    private void analyzeRide() throws IOException, InterruptedException {
        List<Ride> rides = showRides();
        if (!rides.isEmpty()) {
            output.println(controller.analyzeRide(selectRide(rides, "Select a ride number: ")));
        }
    }

    private void compareRides() {
        List<Ride> rides = showRides();
        if (rides.size() < 2) {
            output.println("At least two rides are required for comparison.");
            return;
        }
        Ride first = selectRide(rides, "First ride number: ");
        Ride second = selectRide(rides, "Second ride number (choose a different ride): ");
        output.println(controller.compareRides(first, second));
    }

    private List<Ride> showRides() {
        List<Ride> rides = controller.getRideHistory();
        if (rides.isEmpty()) {
            output.println("No rides available. Import a Garmin CSV using option 2.");
        }
        for (int i = 0; i < rides.size(); i++) {
            Ride ride = rides.get(i);
            output.printf(Locale.ROOT, "%d. %s | %.2f km | %s | average power %d W%n",
                    i + 1, ride.getDate(), ride.getDistance(), DurationFormatter.format(ride.getDuration()), ride.getAvgPower());
        }
        return rides;
    }

    private Ride selectRide(List<Ride> rides, String prompt) {
        int selection = integer(prompt);
        if (selection < 1 || selection > rides.size()) {
            throw new IllegalArgumentException("Choose a ride number from 1 to " + rides.size() + ".");
        }
        return rides.get(selection - 1);
    }

    private void displayPlan(TrainingPlan plan) {
        output.println("Weekly plan generated. Active workouts:");
        if (plan.getWorkouts().isEmpty()) {
            output.println("No active workouts scheduled.");
        }
        for (Workout workout : plan.getWorkouts()) {
            output.printf(Locale.ROOT, "%s | %s | %s | %d W | %s%n",
                    workout.getDate(), workout.getType(), DurationFormatter.format(workout.getDuration()),
                    workout.getTargetPower(), workout.getDescription());
        }
        output.println("Days without active workouts are rest days in this plan. Use option 10 to view the week.");
    }

    private void displayCalendar() {
        String text = read("Date (yyyy-MM-dd, or Enter for today): ");
        LocalDate date = text.isEmpty() ? LocalDate.now() : LocalDate.parse(text);
        output.println(controller.getWorkoutCalendar(date));
    }

    private String read(String prompt) {
        output.print(prompt);
        output.flush();
        if (!input.hasNextLine()) {
            throw new NoSuchElementException("Input ended.");
        }
        return input.nextLine().strip();
    }

    private int integer(String prompt) {
        try {
            return Integer.parseInt(read(prompt));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number for " + prompt.strip());
        }
    }

    private double decimal(String prompt) {
        try {
            return Double.parseDouble(read(prompt));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a number using a decimal point for " + prompt.strip());
        }
    }
}
