package storage;

import model.RiderProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Local properties-file storage for the rider profile, separate from the model. */
public class ProfileStorage {
    private final Path profileFile;

    /** Uses data/rider_profile.properties relative to the application's working directory. */
    public ProfileStorage() {
        this(Path.of("data", "rider_profile.properties"));
    }

    /** Allows an alternative file location, for example for isolated checks. */
    public ProfileStorage(Path profileFile) {
        this.profileFile = Objects.requireNonNull(profileFile, "profileFile is required")
                .toAbsolutePath().normalize();
    }

    /**
     * Saves all profile fields, creating parent directories as needed.
     * Writes a temporary file before replacing any existing profile.
     *
     * @throws IllegalArgumentException if the profile is null
     * @throws IOException if the profile cannot be saved
     */
    public void saveProfile(RiderProfile profile) throws IOException {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required.");
        }
        Properties properties = new Properties();
        properties.setProperty("weight", Double.toString(profile.getWeight()));
        properties.setProperty("ftp", Integer.toString(profile.getFtp()));
        properties.setProperty("maxHR", Integer.toString(profile.getMaxHR()));
        properties.setProperty("restingHR", Integer.toString(profile.getRestingHR()));
        properties.setProperty("availableTrainingDays",
                Integer.toString(profile.getAvailableTrainingDays()));
        properties.setProperty("targetWeight", Double.toString(profile.getTargetWeight()));
        properties.setProperty("targetFTP", Integer.toString(profile.getTargetFTP()));
        properties.setProperty("mainGoal", profile.getMainGoal());

        Files.createDirectories(profileFile.getParent());
        Path temporaryFile = Files.createTempFile(profileFile.getParent(), "profile-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "Rider profile");
            }
            try {
                Files.move(temporaryFile, profileFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, profileFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    /**
     * Loads a profile, or returns empty if no saved file exists.
     *
     * @throws IOException if reading fails or saved values are missing, malformed,
     *                     or rejected by RiderProfile validation
     */
    public Optional<RiderProfile> loadProfile() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(profileFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            throw invalidProfile(exception);
        }

        try {
            return Optional.of(new RiderProfile(
                    Double.parseDouble(required(properties, "weight")),
                    Integer.parseInt(required(properties, "ftp")),
                    Integer.parseInt(required(properties, "maxHR")),
                    Integer.parseInt(required(properties, "restingHR")),
                    Integer.parseInt(required(properties, "availableTrainingDays")),
                    Double.parseDouble(required(properties, "targetWeight")),
                    Integer.parseInt(required(properties, "targetFTP")),
                    required(properties, "mainGoal")));
        } catch (IllegalArgumentException exception) {
            throw invalidProfile(exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or blank property: " + key);
        }
        return value.strip();
    }

    private IOException invalidProfile(IllegalArgumentException cause) {
        return new IOException("Invalid rider profile in " + profileFile + ": "
                + cause.getMessage(), cause);
    }
}
