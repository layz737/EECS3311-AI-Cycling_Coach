package storage;

import model.TrainingPlan;
import model.Workout;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Persists only the current plan; durations retain the model's seconds unit. */
public class TrainingPlanStorage {
    private static final List<String> HEADER = List.of("date", "type", "duration", "targetPower", "description");
    private final Path planFile;
    private boolean loadedSuccessfully;
    private boolean loadFailed;

    public TrainingPlanStorage() {
        this(Path.of("data", "training_plan.csv"));
    }

    public TrainingPlanStorage(Path planFile) {
        this.planFile = Objects.requireNonNull(planFile, "planFile is required").toAbsolutePath().normalize();
    }

    /** Missing files produce an empty plan. A failed load never changes the file. */
    public TrainingPlan loadPlan() throws IOException {
        try {
            TrainingPlan plan = readPlan();
            loadedSuccessfully = true;
            loadFailed = false;
            return plan;
        } catch (IOException exception) {
            loadFailed = true;
            throw new IOException("Cannot load training plan from " + planFile + ": "
                    + exception.getMessage(), exception);
        }
    }

    /** Writes a complete temporary CSV before replacing the current saved plan. */
    public void savePlan(TrainingPlan plan) throws IOException {
        Objects.requireNonNull(plan, "plan is required");
        if (loadFailed) {
            throw new IOException("Training plan was not saved because its previous load failed. "
                    + "Repair the file and load successfully, or restart after repairing it.");
        }
        if (!loadedSuccessfully) {
            loadPlan();
        }
        List<Workout> workouts = new ArrayList<>(plan.getWorkouts());
        workouts.sort(Comparator.comparing(Workout::getDate));
        Files.createDirectories(planFile.getParent());
        Path temporary = Files.createTempFile(planFile.getParent(), "training-plan-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(String.join(",", HEADER));
                writer.newLine();
                for (Workout workout : workouts) {
                    writer.write(workout.getDate() + "," + quote(workout.getType()) + ","
                            + workout.getDuration() + "," + workout.getTargetPower() + ","
                            + quote(workout.getDescription()));
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, planFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, planFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private TrainingPlan readPlan() throws IOException {
        List<Workout> workouts = new ArrayList<>();
        try (PushbackReader reader = new PushbackReader(
                Files.newBufferedReader(planFile, StandardCharsets.UTF_8), 1)) {
            if (!HEADER.equals(readRow(reader, 1))) {
                throw new IOException("Expected saved-plan header: " + String.join(",", HEADER));
            }
            List<String> row;
            int rowNumber = 2;
            while ((row = readRow(reader, rowNumber)) != null) {
                if (row.size() != 5) {
                    throw new IOException("CSV row " + rowNumber + " must contain five fields.");
                }
                try {
                    workouts.add(new Workout(LocalDate.parse(row.get(0).strip()), row.get(1),
                            Double.parseDouble(row.get(2).strip()), Integer.parseInt(row.get(3).strip()), row.get(4)));
                } catch (DateTimeParseException | IllegalArgumentException exception) {
                    throw new IOException("Invalid workout on CSV row " + rowNumber + ": "
                            + exception.getMessage(), exception);
                }
                rowNumber++;
            }
        } catch (NoSuchFileException exception) {
            return new TrainingPlan();
        }
        workouts.sort(Comparator.comparing(Workout::getDate));
        TrainingPlan plan = new TrainingPlan();
        workouts.forEach(plan::addWorkout);
        return plan;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** Reads a logical record, supporting quoted commas, escaped quotes, and multiline text. */
    private static List<String> readRow(PushbackReader reader, int rowNumber) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        boolean sawCharacter = false;
        int character;
        while ((character = reader.read()) != -1) {
            sawCharacter = true;
            char ch = (char) character;
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        closedQuote = true;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
                if (ch != ',') {
                    if (ch == '\r') {
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.unread(next);
                        }
                    }
                    return fields;
                }
            } else if (closedQuote) {
                if (ch != ' ' && ch != '\t') {
                    throw new IOException("CSV row " + rowNumber + ": unexpected text after closing quote.");
                }
            } else if (ch == '"') {
                if (field.length() != 0) {
                    throw new IOException("CSV row " + rowNumber + ": unexpected quote in an unquoted field.");
                }
                quoted = true;
            } else {
                field.append(ch);
            }
        }
        if (quoted) {
            throw new IOException("CSV row " + rowNumber + ": unclosed quoted field.");
        }
        if (!sawCharacter) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }
}
