package service;

import model.Ride;
import model.RideHistory;

import java.io.IOException;
import java.io.PushbackReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Imports cycling activities from an English, metric-unit Garmin history CSV. */
public class CSVImporter {
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "Activity Type", "Date", "Distance", "Time", "Avg Speed", "Avg HR", "Max HR",
            "Avg Power", "Max Power", "Avg Bike Cadence");

    // Add normalized Garmin activity labels here as more cycling types are supported.
    private static final Set<String> CYCLING_TYPES = Set.of(
            "cycling", "road cycling", "indoor cycling", "virtual cycling",
            "mountain biking", "gravel cycling", "cyclocross", "bmx",
            "e-biking", "e-mountain biking", "track cycling", "bike commuting",
            "bike touring", "hand cycling");

    /**
     * Parses and closes the entire file before adding any rides to history.
     * Returns only newly added rides as an unmodifiable list in CSV order.
     * Existing equivalents and repeated rows are skipped. Missing mapped measurements are
     * rejected because Ride has no representation for unknown values.
     *
     * @throws IllegalArgumentException if path or history is null
     * @throws IOException if reading, CSV parsing, or cycling-row validation fails
     */
    public List<Ride> importRide(Path filePath, RideHistory rideHistory) throws IOException {
        if (filePath == null || rideHistory == null) {
            throw new IllegalArgumentException("filePath and rideHistory are required.");
        }
        List<Ride> rides;
        try {
            rides = readRides(filePath);
        } catch (IOException exception) {
            throw new IOException("Cannot import Garmin rides from " + filePath + ": "
                    + exception.getMessage(), exception);
        }
        RideHistory additions = new RideHistory();
        for (Ride ride : rides) {
            if (!rideHistory.containsEquivalentRide(ride)) {
                additions.addRide(ride);
            }
        }
        List<Ride> imported = additions.getRides();
        // Every cycling row, including duplicate rows, was validated before any mutation.
        imported.forEach(rideHistory::addRide);
        return imported;
    }

    private List<Ride> readRides(Path filePath) throws IOException {
        try (PushbackReader reader = new PushbackReader(
                Files.newBufferedReader(filePath, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') {
                reader.unread(first);
            }
            List<String> headers = readRow(reader, 1);
            if (headers == null) {
                throw new IOException("Missing header row.");
            }
            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String name = headers.get(i).strip();
                if (name.isEmpty()) {
                    throw new IOException("Blank header at column " + (i + 1) + ".");
                }
                if (columns.putIfAbsent(name, i) != null) {
                    throw new IOException("Duplicate column: " + name);
                }
            }
            for (String name : REQUIRED_COLUMNS) {
                if (!columns.containsKey(name)) {
                    throw new IOException("Missing required column: " + name);
                }
            }
            List<Ride> rides = new ArrayList<>();
            List<String> row;
            int rowNumber = 2;
            while ((row = readRow(reader, rowNumber)) != null) {
                if (!(row.size() == 1 && row.get(0).isBlank())) {
                    if (row.size() != headers.size()) {
                        throw new IOException("CSV row " + rowNumber + " has " + row.size()
                                + " fields; expected " + headers.size() + ".");
                    }
                    try {
                        String type = required(row, columns, "Activity Type");
                        if (CYCLING_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
                            rides.add(parseRide(row, columns));
                        }
                    } catch (IllegalArgumentException exception) {
                        throw new IOException("CSV row " + rowNumber + ": "
                                + exception.getMessage(), exception);
                    }
                }
                rowNumber++;
            }
            return List.copyOf(rides);
        }
    }

    private static Ride parseRide(List<String> row, Map<String, Integer> columns) {
        return new Ride(parseDate(required(row, columns, "Date")),
                decimal(row, columns, "Distance"),
                parseDuration(required(row, columns, "Time")),
                decimal(row, columns, "Avg Speed"),
                integer(row, columns, "Avg HR"), integer(row, columns, "Max HR"),
                integer(row, columns, "Avg Power"), integer(row, columns, "Max Power"),
                integer(row, columns, "Avg Bike Cadence"));
    }

    private static String required(List<String> row, Map<String, Integer> columns, String key) {
        String value = row.get(columns.get(key)).strip();
        if (value.isEmpty() || value.equals("--")) {
            throw new IllegalArgumentException("Missing value for column '" + key
                    + "'; Ride cannot represent unknown measurements. No zero fallback is used.");
        }
        return value;
    }

    private static LocalDate parseDate(String text) {
        try {
            // Garmin's yyyy-MM-dd HH:mm:ss, also accepting ISO T and fractional seconds.
            if (text.length() == 10) {
                return LocalDate.parse(text);
            }
            return LocalDateTime.parse(text.replace(' ', 'T')).toLocalDate();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Column 'Date' requires yyyy-MM-dd HH:mm:ss"
                    + " (or an ISO date/date-time).", exception);
        }
    }

    private static double parseDuration(String text) {
        if (!text.matches("\\d+:[0-5]\\d:[0-5]\\d(?:\\.\\d+)?")) {
            throw new IllegalArgumentException("Column 'Time' requires HH:MM:SS"
                    + " with optional fractional seconds.");
        }
        String[] parts = text.split(":");
        double seconds = Double.parseDouble(parts[0]) * 3600
                + Integer.parseInt(parts[1]) * 60 + Double.parseDouble(parts[2]);
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Column 'Time' must be finite and positive.");
        }
        return seconds;
    }

    private static BigDecimal number(List<String> row, Map<String, Integer> columns, String key) {
        String text = required(row, columns, key);
        // English decimals; grouping commas are accepted only in correctly grouped numbers.
        if (!text.matches("[+-]?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?")) {
            throw new IllegalArgumentException("Column '" + key + "' requires a numeric value.");
        }
        return new BigDecimal(text.replace(",", ""));
    }

    private static double decimal(List<String> row, Map<String, Integer> columns, String key) {
        double value = number(row, columns, key).doubleValue();
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Column '" + key + "' must be finite and non-negative.");
        }
        return value;
    }

    private static int integer(List<String> row, Map<String, Integer> columns, String key) {
        try {
            int value = number(row, columns, key).intValueExact();
            if (value < 0) {
                throw new IllegalArgumentException("Column '" + key + "' must not be negative.");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Column '" + key
                    + "' requires a whole number within the int range; values are not rounded.", exception);
        }
    }

    /** Reads one logical CSV record, including escaped quotes and embedded line breaks. */
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
                    throw new IOException("CSV row " + rowNumber + ": unexpected character after closing quote.");
                }
            } else if (ch == '"') {
                if (!field.toString().isBlank()) {
                    throw new IOException("CSV row " + rowNumber + ": quote inside an unquoted field.");
                }
                field.setLength(0);
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
