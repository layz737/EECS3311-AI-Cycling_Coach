package service;

import model.Ride;
import model.RideHistory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Imports one ride from a simple, unquoted UTF-8 summary CSV for Stage 1 F02. */
public class CSVImporter {
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "date", "distance", "duration", "avgSpeed", "avgHR", "maxHR",
            "avgPower", "maxPower", "avgCadence");

    /**
     * Reads and closes the entire file, validates a Ride, then adds it to history.
     * Header names are case-sensitive and may appear in any order. Extra columns
     * are allowed but ignored. Quoted fields and embedded newlines are unsupported.
     *
     * @throws IllegalArgumentException if the path or history is null
     * @throws IOException if reading fails or the CSV contains invalid data
     */
    public Ride importRide(Path filePath, RideHistory rideHistory) throws IOException {
        if (filePath == null || rideHistory == null) {
            throw new IllegalArgumentException("filePath and rideHistory are required.");
        }

        Ride ride;
        try {
            ride = readRide(filePath);
        } catch (IOException exception) {
            throw new IOException("Cannot import ride from " + filePath + ": "
                    + exception.getMessage(), exception);
        }
        // This is the only history mutation, after parsing, validation, and file close succeed.
        rideHistory.addRide(ride);
        return ride;
    }

    private Ride readRide(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Missing header row.");
            }
            // Accept the UTF-8 byte-order mark emitted by some spreadsheet exporters.
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String[] headers = splitRow(headerLine, "Header row");
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].isBlank()) {
                    throw new IOException("Blank column name at position " + (i + 1) + ".");
                }
                if (columns.putIfAbsent(headers[i], i) != null) {
                    throw new IOException("Duplicate column: " + headers[i]);
                }
            }
            for (String required : REQUIRED_COLUMNS) {
                if (!columns.containsKey(required)) {
                    throw new IOException("Missing required column: " + required);
                }
            }

            String dataLine = reader.readLine();
            if (dataLine == null || dataLine.isBlank()) {
                throw new IOException("Exactly one non-blank ride data row is required.");
            }
            String[] values = splitRow(dataLine, "Ride data row");
            if (values.length != headers.length) {
                throw new IOException("Ride data row has " + values.length + " fields; expected "
                        + headers.length + ".");
            }
            if (reader.readLine() != null) {
                throw new IOException("Exactly one ride data row is allowed; found an additional row.");
            }
            for (String required : REQUIRED_COLUMNS) {
                if (values[columns.get(required)].isBlank()) {
                    throw new IOException("Missing or blank value for column: " + required);
                }
            }

            LocalDate date;
            try {
                date = LocalDate.parse(values[columns.get("date")]);
            } catch (DateTimeParseException exception) {
                throw new IOException("Invalid date; expected an ISO date such as 2026-11-07.", exception);
            }
            try {
                return new Ride(date,
                        decimal(values, columns, "distance"),
                        decimal(values, columns, "duration"),
                        decimal(values, columns, "avgSpeed"),
                        integer(values, columns, "avgHR"),
                        integer(values, columns, "maxHR"),
                        integer(values, columns, "avgPower"),
                        integer(values, columns, "maxPower"),
                        integer(values, columns, "avgCadence"));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid ride data: " + exception.getMessage(), exception);
            }
        }
    }

    private static String[] splitRow(String line, String label) throws IOException {
        if (line.indexOf('"') >= 0) {
            throw new IOException(label + ": quoted fields are not supported by this summary format.");
        }
        String[] fields = line.split(",", -1);
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].strip();
        }
        return fields;
    }

    private static double decimal(String[] values, Map<String, Integer> columns, String key)
            throws IOException {
        try {
            return Double.parseDouble(values[columns.get(key)]);
        } catch (NumberFormatException exception) {
            throw new IOException("Column " + key + " requires a number.", exception);
        }
    }

    private static int integer(String[] values, Map<String, Integer> columns, String key)
            throws IOException {
        try {
            return Integer.parseInt(values[columns.get(key)]);
        } catch (NumberFormatException exception) {
            throw new IOException("Column " + key + " requires a whole number within the int range.", exception);
        }
    }
}
