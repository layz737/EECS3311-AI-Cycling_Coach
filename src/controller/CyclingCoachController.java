package controller;

import model.Ride;
import model.RideHistory;
import model.RiderProfile;
import model.TrainingPlan;
import service.CSVImporter;
import service.PersonalBestService;
import service.ProgressTracker;
import service.RideComparisonService;
import service.WorkoutCalendarService;
import storage.ProfileStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Stage 1 facade and MVC controller shared by future GUI and CLI views.
 * Coordinates deterministic features; AI methods will be added with their services.
 */
public class CyclingCoachController {
    private RiderProfile profile;
    private final RideHistory rideHistory;
    private final TrainingPlan trainingPlan;
    private ProgressTracker progressTracker;
    private final CSVImporter csvImporter;
    private final RideComparisonService comparisonService;
    private final PersonalBestService personalBestService;
    private final WorkoutCalendarService calendarService;
    private final ProfileStorage profileStorage;

    /** Creates an empty session using default local profile storage. Call loadProfile() at startup. */
    public CyclingCoachController() {
        this(null, new RideHistory(), null, new CSVImporter(), new RideComparisonService(),
                new PersonalBestService(), new ProfileStorage());
    }

    /**
     * Accepts an optional initial profile and plan, plus required session dependencies.
     * Bound observers are constructed and registered once here (or on first profile setup).
     * An injected profile is treated as initialized and is not automatically saved.
     */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage) {
        this.rideHistory = Objects.requireNonNull(rideHistory, "rideHistory is required");
        this.trainingPlan = trainingPlan;
        this.csvImporter = Objects.requireNonNull(csvImporter, "csvImporter is required");
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService is required");
        this.personalBestService = Objects.requireNonNull(personalBestService, "personalBestService is required");
        this.profileStorage = Objects.requireNonNull(profileStorage, "profileStorage is required");
        this.calendarService = new WorkoutCalendarService(trainingPlan, rideHistory);
        if (trainingPlan != null) {
            trainingPlan.addObserver(calendarService);
        }
        if (profile != null) {
            applyProfile(profile);
        }
    }

    /**
     * Loads the saved profile without saving it again. Returns false if no file exists,
     * leaving any current profile unchanged. Invalid/unreadable storage propagates IOException.
     */
    public boolean loadProfile() throws IOException {
        Optional<RiderProfile> saved = profileStorage.loadProfile();
        if (saved.isEmpty()) {
            return false;
        }
        applyProfile(saved.orElseThrow());
        return true;
    }

    /** Read access to the active profile; edits through this controller are saved automatically. */
    public Optional<RiderProfile> getProfile() {
        return Optional.ofNullable(profile);
    }

    /** Creates or updates the profile, using a validated copy before saving or changing live state. */
    public void setUpProfile(RiderProfile values) throws IOException {
        if (values == null) {
            throw new IllegalArgumentException("profile is required.");
        }
        setUpProfile(values.getWeight(), values.getFtp(), values.getMaxHR(), values.getRestingHR(),
                values.getAvailableTrainingDays(), values.getTargetWeight(),
                values.getTargetFTP(), values.getMainGoal());
    }

    /** Validation and save failures leave the active profile and its progress records unchanged. */
    public void setUpProfile(double weight, int ftp, int maxHR, int restingHR,
                             int availableTrainingDays, double targetWeight,
                             int targetFTP, String mainGoal) throws IOException {
        RiderProfile candidate = new RiderProfile(weight, ftp, maxHR, restingHR,
                availableTrainingDays, targetWeight, targetFTP, mainGoal);
        profileStorage.saveProfile(candidate);
        applyProfile(candidate);
    }

    /** Updates an existing profile; first-time creation uses setUpProfile(). */
    public void updateProfile(double weight, int ftp, int maxHR, int restingHR,
                              int availableTrainingDays, double targetWeight,
                              int targetFTP, String mainGoal) throws IOException {
        requireProfile();
        setUpProfile(weight, ftp, maxHR, restingHR, availableTrainingDays,
                targetWeight, targetFTP, mainGoal);
    }

    /** Delegates Garmin parsing and all-or-nothing validation to CSVImporter. */
    public List<Ride> importRides(Path filePath) throws IOException {
        return csvImporter.importRide(filePath, rideHistory);
    }

    /** Returns the model's unmodifiable snapshot, rather than exposing mutable history. */
    public List<Ride> getRideHistory() {
        return rideHistory.getRides();
    }

    public String compareRides(Ride ride1, Ride ride2) {
        return comparisonService.compareRides(ride1, ride2);
    }

    /** Requires a profile; the tracker supplies insufficient-history messages when needed. */
    public String getProgress() {
        requireProfile();
        return progressTracker.getProgress();
    }

    /** Formats the four service-selected personal bests; date filtering remains in the service. */
    public String getPersonalBests() {
        return String.join("\n", "Personal bests (past calendar month):",
                best("Longest distance", personalBestService.getLongestRide(rideHistory), Ride::getDistance, "km"),
                best("Highest average power", personalBestService.getHighestAvgPower(rideHistory), Ride::getAvgPower, "W"),
                best("Highest maximum power", personalBestService.getHighestMaxPower(rideHistory), Ride::getMaxPower, "W"),
                best("Fastest average speed", personalBestService.getFastestAvgSpeed(rideHistory), Ride::getAvgSpeed, "km/h"));
    }

    public String getWorkoutCalendar(LocalDate date) {
        return calendarService.getWorkoutCalendar(date);
    }

    private void applyProfile(RiderProfile values) {
        if (profile == null) {
            profile = values;
            progressTracker = new ProgressTracker(profile);
            profile.addObserver(progressTracker);
            progressTracker.update();
        } else {
            // Preserve profile identity, observer subscriptions, and the tracker's existing history.
            profile.updateProfile(values.getWeight(), values.getFtp(), values.getMaxHR(),
                    values.getRestingHR(), values.getAvailableTrainingDays(),
                    values.getTargetWeight(), values.getTargetFTP(), values.getMainGoal());
        }
    }

    private void requireProfile() {
        if (profile == null) {
            throw new IllegalStateException("Set up or load a rider profile first.");
        }
    }

    private static String best(String label, Optional<Ride> ride, ToDoubleFunction<Ride> metric, String unit) {
        return ride.map(value -> String.format(Locale.ROOT, "%s: %.2f %s on %s",
                label, metric.applyAsDouble(value), unit, value.getDate()))
                .orElse(label + ": No rides available in the past month.");
    }
}
