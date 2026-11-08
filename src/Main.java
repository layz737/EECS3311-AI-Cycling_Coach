import controller.CyclingCoachController;
import model.RideHistory;
import model.RiderProfile;
import model.TrainingPlan;
import service.CSVImporter;
import service.PersonalBestService;
import service.RideComparisonService;
import storage.ProfileStorage;
import storage.RideHistoryStorage;
import storage.FitnessRecordStorage;
import storage.TrainingPlanStorage;
import ui.CyclingCoachCLI;
import ui.CyclingCoachGUI;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.Objects;

/** Application entry point: GUI by default, or CLI with the argument "cli". */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("cli")) {
            System.err.println("Usage: java Main [cli]");
            return;
        }
        boolean cli = args.length == 1;
        if (!cli && GraphicsEnvironment.isHeadless()) {
            System.err.println("No graphical display is available. Launch the CLI with: java Main cli");
            return;
        }

        CyclingCoachController controller = createController(new ProfileStorage(), new RideHistoryStorage(),
                new FitnessRecordStorage(), new TrainingPlanStorage());
        if (cli) {
            new CyclingCoachCLI(controller).start();
        } else {
            SwingUtilities.invokeLater(() -> new CyclingCoachGUI(controller).start());
        }
    }

    /**
     * Wires one session. Package access permits checks with isolated profile storage.
     * The controller owns bound-service creation and Observer registration:
     * profile -> ProgressTracker, and plan -> WorkoutCalendarService.
     * It also creates RiderContextService and TrainingPlanParser once, and lazily
     * creates the shared DeepSeek client and the three AI services on first AI use.
     */
    static CyclingCoachController createController(ProfileStorage storage) {
        return createController(storage, new RideHistoryStorage());
    }

    static CyclingCoachController createController(ProfileStorage storage, RideHistoryStorage historyStorage) {
        return createController(storage, historyStorage, new FitnessRecordStorage());
    }

    static CyclingCoachController createController(ProfileStorage storage, RideHistoryStorage historyStorage,
                                                   FitnessRecordStorage fitnessStorage) {
        return createController(storage, historyStorage, fitnessStorage, new TrainingPlanStorage());
    }

    static CyclingCoachController createController(ProfileStorage storage, RideHistoryStorage historyStorage,
                                                   FitnessRecordStorage fitnessStorage, TrainingPlanStorage planStorage) {
        Objects.requireNonNull(storage, "storage is required");
        Objects.requireNonNull(historyStorage, "historyStorage is required");
        Objects.requireNonNull(fitnessStorage, "fitnessStorage is required");
        Objects.requireNonNull(planStorage, "planStorage is required");
        RiderProfile profile = null;
        try {
            profile = storage.loadProfile().orElse(null);
        } catch (IOException exception) {
            // Keep the file intact and allow the views to report the error or set up a profile.
            System.err.println("Could not load the saved profile: " + exception.getMessage());
        }

        RideHistory history;
        try {
            history = historyStorage.loadRideHistory();
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
            // Keep startup usable, but retain the failed-load storage guard against overwrites.
            history = new RideHistory();
        }
        TrainingPlan plan;
        try {
            plan = planStorage.loadPlan();
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
            // The failed-load guard prevents this empty fallback from overwriting saved data.
            plan = new TrainingPlan();
        }
        return new CyclingCoachController(profile, history, plan,
                new CSVImporter(), new RideComparisonService(), new PersonalBestService(), storage,
                historyStorage, fitnessStorage, planStorage);
    }
}
