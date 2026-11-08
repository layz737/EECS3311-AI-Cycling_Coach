package controller;

import ai.DeepSeekAdapter;
import ai.DeepSeekService;
import ai.LLMClient;
import model.Ride;
import model.FitnessRecord;
import model.RideHistory;
import model.RiderProfile;
import model.TrainingPlan;
import model.Workout;
import service.CSVImporter;
import service.AIChatService;
import service.RideAnalysisService;
import service.RiderContextService;
import service.TrainingPlanGenerator;
import service.TrainingPlanParser;
import service.PersonalBestService;
import service.ProgressTracker;
import service.RideComparisonService;
import service.WorkoutCalendarService;
import storage.ProfileStorage;
import storage.RideHistoryStorage;
import storage.FitnessRecordStorage;
import storage.TrainingPlanStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.function.Supplier;

/**
 * Stage 1 facade and MVC controller shared by future GUI and CLI views.
 * Coordinates deterministic and AI features without duplicating service logic.
 */
public class CyclingCoachController {
    private RiderProfile profile;
    private final RideHistory rideHistory;
    private TrainingPlan trainingPlan;
    private ProgressTracker progressTracker;
    private final CSVImporter csvImporter;
    private final RideComparisonService comparisonService;
    private final PersonalBestService personalBestService;
    private WorkoutCalendarService calendarService;
    private final ProfileStorage profileStorage;
    private final RideHistoryStorage rideHistoryStorage;
    private final FitnessRecordStorage fitnessRecordStorage;
    private final TrainingPlanStorage trainingPlanStorage;
    private final List<FitnessRecord> loadedFitnessRecords;
    private IOException fitnessHistoryFailure;
    private final Supplier<LLMClient> llmClientFactory;
    private final RiderContextService contextService;
    private final TrainingPlanParser planParser;
    private AIChatService aiChatService;
    private RideAnalysisService rideAnalysisService;
    private TrainingPlanGenerator trainingPlanGenerator;

    /** Loads local ride history; profile loading remains available through loadProfile(). */
    public CyclingCoachController() {
        this(new ProfileStorage(), new RideHistoryStorage(), new TrainingPlanStorage());
    }

    private CyclingCoachController(ProfileStorage profileStorage, RideHistoryStorage rideHistoryStorage,
                                   TrainingPlanStorage trainingPlanStorage) {
        this(null, loadHistory(rideHistoryStorage), loadPlan(trainingPlanStorage), new CSVImporter(), new RideComparisonService(),
                new PersonalBestService(), profileStorage, rideHistoryStorage, new FitnessRecordStorage(), trainingPlanStorage);
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
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, new RideHistoryStorage());
    }

    /** Injects the same storage instance used to load the shared history at startup. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 RideHistoryStorage rideHistoryStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, rideHistoryStorage, new FitnessRecordStorage());
    }

    /** Loads fitness snapshots before constructing and registering the session's tracker. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 RideHistoryStorage rideHistoryStorage, FitnessRecordStorage fitnessRecordStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, rideHistoryStorage, fitnessRecordStorage, new TrainingPlanStorage());
    }

    /** Uses the loaded active plan and its storage, preserving the calendar's model identity. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 RideHistoryStorage rideHistoryStorage, FitnessRecordStorage fitnessRecordStorage,
                                 TrainingPlanStorage trainingPlanStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, () -> new DeepSeekAdapter(new DeepSeekService()),
                new RiderContextService(), new TrainingPlanParser(), rideHistoryStorage, fitnessRecordStorage, trainingPlanStorage);
    }

    /** Injects a provider-independent LLM client and shared AI infrastructure. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 LLMClient llmClient, RiderContextService contextService,
                                 TrainingPlanParser planParser) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, llmClient, contextService, planParser, new RideHistoryStorage());
    }

    /** Full injection constructor, including isolated persistence and provider-independent AI. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 LLMClient llmClient, RiderContextService contextService,
                                 TrainingPlanParser planParser, RideHistoryStorage rideHistoryStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, llmClient, contextService, planParser, rideHistoryStorage, new FitnessRecordStorage());
    }

    /** Full injection including fitness-history persistence. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 LLMClient llmClient, RiderContextService contextService,
                                 TrainingPlanParser planParser, RideHistoryStorage rideHistoryStorage,
                                 FitnessRecordStorage fitnessRecordStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, llmClient, contextService, planParser, rideHistoryStorage, fitnessRecordStorage,
                new TrainingPlanStorage());
    }

    /** Full injection, including active-plan persistence. */
    public CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                 TrainingPlan trainingPlan, CSVImporter csvImporter,
                                 RideComparisonService comparisonService,
                                 PersonalBestService personalBestService, ProfileStorage profileStorage,
                                 LLMClient llmClient, RiderContextService contextService,
                                 TrainingPlanParser planParser, RideHistoryStorage rideHistoryStorage,
                                 FitnessRecordStorage fitnessRecordStorage, TrainingPlanStorage trainingPlanStorage) {
        this(profile, rideHistory, trainingPlan, csvImporter, comparisonService, personalBestService,
                profileStorage, clientFactory(llmClient), contextService, planParser, rideHistoryStorage,
                fitnessRecordStorage, trainingPlanStorage);
    }

    private CyclingCoachController(RiderProfile profile, RideHistory rideHistory,
                                  TrainingPlan trainingPlan, CSVImporter csvImporter,
                                  RideComparisonService comparisonService,
                                  PersonalBestService personalBestService, ProfileStorage profileStorage,
                                  Supplier<LLMClient> llmClientFactory, RiderContextService contextService,
                                  TrainingPlanParser planParser, RideHistoryStorage rideHistoryStorage,
                                  FitnessRecordStorage fitnessRecordStorage, TrainingPlanStorage trainingPlanStorage) {
        this.rideHistory = Objects.requireNonNull(rideHistory, "rideHistory is required");
        this.trainingPlan = trainingPlan;
        this.csvImporter = Objects.requireNonNull(csvImporter, "csvImporter is required");
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService is required");
        this.personalBestService = Objects.requireNonNull(personalBestService, "personalBestService is required");
        this.profileStorage = Objects.requireNonNull(profileStorage, "profileStorage is required");
        this.rideHistoryStorage = Objects.requireNonNull(rideHistoryStorage, "rideHistoryStorage is required");
        this.fitnessRecordStorage = Objects.requireNonNull(fitnessRecordStorage, "fitnessRecordStorage is required");
        this.trainingPlanStorage = Objects.requireNonNull(trainingPlanStorage, "trainingPlanStorage is required");
        List<FitnessRecord> fitnessRecords;
        try {
            fitnessRecords = fitnessRecordStorage.loadRecords();
        } catch (IOException exception) {
            fitnessHistoryFailure = exception;
            fitnessRecords = List.of();
            System.err.println(exception.getMessage());
        }
        this.loadedFitnessRecords = fitnessRecords;
        this.llmClientFactory = Objects.requireNonNull(llmClientFactory, "llmClientFactory is required");
        this.contextService = Objects.requireNonNull(contextService, "contextService is required");
        this.planParser = Objects.requireNonNull(planParser, "planParser is required");
        this.calendarService = new WorkoutCalendarService(trainingPlan, rideHistory);
        if (trainingPlan != null) {
            trainingPlan.addObserver(calendarService);
        }
        if (profile != null) {
            applyProfile(profile, false);
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
        applyProfile(saved.orElseThrow(), false);
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
        requireFitnessHistory();
        profileStorage.saveProfile(candidate);
        applyProfile(candidate, true);
        try {
            fitnessRecordStorage.saveRecords(progressTracker.getRecords());
        } catch (IOException exception) {
            throw new IOException("Profile was saved, but fitness history could not be saved: "
                    + exception.getMessage(), exception);
        }
    }

    /** Updates an existing profile; first-time creation uses setUpProfile(). */
    public void updateProfile(double weight, int ftp, int maxHR, int restingHR,
                              int availableTrainingDays, double targetWeight,
                              int targetFTP, String mainGoal) throws IOException {
        requireProfile();
        setUpProfile(weight, ftp, maxHR, restingHR, availableTrainingDays,
                targetWeight, targetFTP, mainGoal);
    }

    /** Stages parsing/deduplication, saves first, then commits new rides to the shared model. */
    public List<Ride> importRides(Path filePath) throws IOException {
        RideHistory staged = new RideHistory();
        rideHistory.getRides().forEach(staged::addRide);
        List<Ride> imported = csvImporter.importRide(filePath, staged);
        rideHistoryStorage.saveRideHistory(staged);
        imported.forEach(rideHistory::addRide);
        return imported;
    }

    private static RideHistory loadHistory(RideHistoryStorage storage) {
        try {
            return storage.loadRideHistory();
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
            // The same storage instance blocks saves after a failed load.
            return new RideHistory();
        }
    }

    private static TrainingPlan loadPlan(TrainingPlanStorage storage) {
        try {
            return storage.loadPlan();
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
            // Retain the same storage instance so a failed load blocks replacement saves.
            return new TrainingPlan();
        }
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
        if (fitnessHistoryFailure != null) {
            throw new IllegalStateException("Fitness history is unavailable: "
                    + fitnessHistoryFailure.getMessage(), fitnessHistoryFailure);
        }
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

    /** Safe read access for displaying the loaded plan without generating a new one. */
    public List<Workout> getTrainingPlanWorkouts() {
        return trainingPlan == null ? List.of() : trainingPlan.getWorkouts();
    }

    public String analyzeRide(Ride ride) throws IOException, InterruptedException {
        initializeAI();
        return rideAnalysisService.analyzeRide(ride);
    }

    /** Publishes a fully validated plan and rebinds the calendar only after generation succeeds. */
    public TrainingPlan generateTrainingPlan() throws IOException, InterruptedException {
        initializeAI();
        TrainingPlan generated = trainingPlanGenerator.generatePlan();
        trainingPlanStorage.savePlan(generated);
        WorkoutCalendarService nextCalendar = new WorkoutCalendarService(generated, rideHistory);
        generated.addObserver(nextCalendar);
        if (trainingPlan != null) {
            trainingPlan.removeObserver(calendarService);
        }
        trainingPlan = generated;
        calendarService = nextCalendar;
        generated.notifyObservers();
        return generated;
    }

    public String askAI(String question) throws IOException, InterruptedException {
        initializeAI();
        return aiChatService.askQuestion(question);
    }

    public String analyzeWeaknesses() throws IOException, InterruptedException {
        initializeAI();
        return aiChatService.analyzeWeaknesses();
    }

    public String getWeeklySummary() throws IOException, InterruptedException {
        initializeAI();
        return aiChatService.generateWeeklySummary();
    }

    private void initializeAI() {
        requireProfile();
        if (aiChatService == null) {
            LLMClient client = Objects.requireNonNull(llmClientFactory.get(), "LLM client is required");
            aiChatService = new AIChatService(client, contextService, profile, rideHistory);
            rideAnalysisService = new RideAnalysisService(client, contextService, profile);
            trainingPlanGenerator = new TrainingPlanGenerator(client, contextService, profile, rideHistory, planParser);
        }
    }

    private static Supplier<LLMClient> clientFactory(LLMClient client) {
        Objects.requireNonNull(client, "llmClient is required");
        return () -> client;
    }

    private void applyProfile(RiderProfile values, boolean recordInitialValues) {
        if (profile == null) {
            profile = values;
            progressTracker = new ProgressTracker(profile, loadedFitnessRecords);
            profile.addObserver(progressTracker);
            if (fitnessHistoryFailure == null && (recordInitialValues || progressTracker.getRecords().isEmpty())) {
                progressTracker.update();
                if (!recordInitialValues) {
                    try {
                        fitnessRecordStorage.saveRecords(progressTracker.getRecords());
                    } catch (IOException exception) {
                        fitnessHistoryFailure = exception;
                        System.err.println("Cannot save initial fitness baseline: " + exception.getMessage());
                    }
                }
            }
        } else {
            // Preserve profile identity, observer subscriptions, and the tracker's existing history.
            profile.updateProfile(values.getWeight(), values.getFtp(), values.getMaxHR(),
                    values.getRestingHR(), values.getAvailableTrainingDays(),
                    values.getTargetWeight(), values.getTargetFTP(), values.getMainGoal());
        }
    }

    private void requireFitnessHistory() throws IOException {
        if (fitnessHistoryFailure != null) {
            throw new IOException("Profile changes are blocked because fitness history could not be loaded/saved. "
                    + "Repair the storage problem and restart. " + fitnessHistoryFailure.getMessage(), fitnessHistoryFailure);
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
