package storage;

import model.Ride;
import model.RideHistory;

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
import java.util.Objects;

/** Stores model summaries as UTF-8 CSV, independently of Garmin's import format. */
public class RideHistoryStorage {
    private static final String HEADER = "date,distance,duration,avgSpeed,avgHR,maxHR,avgPower,maxPower,avgCadence";
    private final Path historyFile;
    private boolean loadedSuccessfully;
    private boolean loadFailed;

    public RideHistoryStorage() {
        this(Path.of("data", "ride_history.csv"));
    }

    public RideHistoryStorage(Path historyFile) {
        this.historyFile = Objects.requireNonNull(historyFile, "historyFile is required")
                .toAbsolutePath().normalize();
    }

    /** Missing files yield an empty history; malformed files are never modified. */
    public RideHistory loadRideHistory() throws IOException {
        try {
            RideHistory history = readHistory();
            loadedSuccessfully = true;
            loadFailed = false;
            return history;
        } catch (IOException exception) {
            loadFailed = true;
            throw new IOException("Cannot load ride history from " + historyFile + ": "
                    + exception.getMessage(), exception);
        }
    }

    /** Writes a complete temporary file before replacing the saved history. */
    public void saveRideHistory(RideHistory history) throws IOException {
        Objects.requireNonNull(history, "history is required");
        if (loadFailed) {
            throw new IOException("Ride history was not saved because its previous load failed. "
                    + "Repair the saved file and load it successfully, or restart after repairing it.");
        }
        // Even a new storage instance must validate an existing file before overwriting it.
        if (!loadedSuccessfully) {
            loadRideHistory();
        }
        Files.createDirectories(historyFile.getParent());
        Path temporary = Files.createTempFile(historyFile.getParent(), "ride-history-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();
                for (Ride ride : history.getRides()) {
                    writer.write(ride.getDate() + "," + ride.getDistance() + "," + ride.getDuration()
                            + "," + ride.getAvgSpeed() + "," + ride.getAvgHR() + "," + ride.getMaxHR()
                            + "," + ride.getAvgPower() + "," + ride.getMaxPower() + "," + ride.getAvgCadence());
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

    private RideHistory readHistory() throws IOException {
        RideHistory history = new RideHistory();
        try (BufferedReader reader = Files.newBufferedReader(historyFile, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw new IOException("Expected saved-history header: " + HEADER);
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] values = line.split(",", -1);
                if (values.length != 9) {
                    throw new IOException("Line " + lineNumber + " must have exactly nine fields.");
                }
                for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].strip();
                    if (values[i].isEmpty()) {
                        throw new IOException("Missing field " + (i + 1) + " on line " + lineNumber + ".");
                    }
                }
                try {
                    history.addRide(new Ride(LocalDate.parse(values[0]), Double.parseDouble(values[1]),
                            Double.parseDouble(values[2]), Double.parseDouble(values[3]),
                            Integer.parseInt(values[4]), Integer.parseInt(values[5]),
                            Integer.parseInt(values[6]), Integer.parseInt(values[7]), Integer.parseInt(values[8])));
                } catch (DateTimeParseException | IllegalArgumentException exception) {
                    throw new IOException("Invalid ride on line " + lineNumber + ": "
                            + exception.getMessage(), exception);
                }
            }
        } catch (NoSuchFileException exception) {
            return new RideHistory();
        }
        return history;
    }
}
