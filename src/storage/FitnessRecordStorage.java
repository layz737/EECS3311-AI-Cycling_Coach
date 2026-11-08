package storage;

import model.FitnessRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
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

/** Local UTF-8 CSV persistence for dated fitness snapshots. */
public class FitnessRecordStorage {
    private static final String HEADER = "date,ftp,weight";
    private final Path historyFile;
    private boolean loadedSuccessfully;
    private boolean loadFailed;

    public FitnessRecordStorage() {
        this(Path.of("data", "fitness_history.csv"));
    }

    public FitnessRecordStorage(Path historyFile) {
        this.historyFile = Objects.requireNonNull(historyFile, "historyFile is required")
                .toAbsolutePath().normalize();
    }

    /** Returns an unmodifiable chronological list, or an empty list for a missing file. */
    public List<FitnessRecord> loadRecords() throws IOException {
        try {
            List<FitnessRecord> records = readRecords();
            loadedSuccessfully = true;
            loadFailed = false;
            return records;
        } catch (IOException exception) {
            loadFailed = true;
            throw new IOException("Cannot load fitness history from " + historyFile + ": "
                    + exception.getMessage(), exception);
        }
    }

    /** Replaces saved records only after a complete temporary file has been written. */
    public void saveRecords(List<FitnessRecord> records) throws IOException {
        List<FitnessRecord> sorted = new ArrayList<>(List.copyOf(records));
        sorted.sort(Comparator.comparing(FitnessRecord::getDate));
        if (loadFailed) {
            throw new IOException("Fitness history was not saved because its previous load failed. "
                    + "Repair the file and load successfully, or restart after repairing it.");
        }
        if (!loadedSuccessfully) {
            loadRecords();
        }
        Files.createDirectories(historyFile.getParent());
        Path temporary = Files.createTempFile(historyFile.getParent(), "fitness-history-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();
                for (FitnessRecord record : sorted) {
                    writer.write(record.getDate() + "," + record.getFtp() + "," + record.getWeight());
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private List<FitnessRecord> readRecords() throws IOException {
        List<FitnessRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(historyFile, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("Expected saved-history header: " + HEADER);
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split(",", -1);
                if (fields.length != 3) {
                    throw new IOException("Line " + lineNumber + " must have exactly three fields.");
                }
                for (int i = 0; i < fields.length; i++) {
                    fields[i] = fields[i].strip();
                    if (fields[i].isEmpty()) {
                        throw new IOException("Missing field " + (i + 1) + " on line " + lineNumber + ".");
                    }
                }
                try {
                    records.add(new FitnessRecord(LocalDate.parse(fields[0]),
                            Integer.parseInt(fields[1]), Double.parseDouble(fields[2])));
                } catch (DateTimeParseException | IllegalArgumentException exception) {
                    throw new IOException("Invalid fitness record on line " + lineNumber + ": "
                            + exception.getMessage(), exception);
                }
            }
        } catch (NoSuchFileException exception) {
            return List.of();
        }
        records.sort(Comparator.comparing(FitnessRecord::getDate));
        return List.copyOf(records);
    }
}
