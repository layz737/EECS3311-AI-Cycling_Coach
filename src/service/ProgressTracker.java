package service;

import model.FitnessRecord;
import model.RiderProfile;
import observer.Observer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Tracks dated fitness measurements and progress toward the rider's current goals. */
public class ProgressTracker implements Observer {
    private final RiderProfile profile;
    private final List<FitnessRecord> records = new ArrayList<>();

    /**
     * Binds this tracker to a profile. The caller registers it using
     * profile.addObserver(tracker), and may call update() to capture an initial baseline.
     */
    public ProgressTracker(RiderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required.");
        }
        this.profile = profile;
    }

    /** Records today's values using the system default timezone. */
    @Override
    public void update() {
        addRecord(new FitnessRecord(LocalDate.now(), profile.getFtp(), profile.getWeight()));
    }

    /**
     * Adds a record chronologically. Skips values identical to the last snapshot
     * on that date; changed values and a return to earlier values are retained.
     * For equal dates, insertion order determines which snapshot is newest.
     */
    public void addRecord(FitnessRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record is required.");
        }
        for (int i = records.size() - 1; i >= 0; i--) {
            FitnessRecord previous = records.get(i);
            if (previous.getDate().equals(record.getDate())) {
                if (previous.getFtp() == record.getFtp()
                        && previous.getWeight() == record.getWeight()) {
                    return;
                }
                break;
            }
        }
        records.add(record);
        records.sort(Comparator.comparing(FitnessRecord::getDate));
    }

    /** Returns an unmodifiable chronological snapshot. */
    public List<FitnessRecord> getRecords() {
        return List.copyOf(records);
    }

    /** Signed change in watts from the earliest to newest record, or zero with fewer than two. */
    public int getFTPChange() {
        if (records.size() < 2) {
            return 0;
        }
        return newest().getFtp() - records.get(0).getFtp();
    }

    /** Signed change in kilograms; weight loss is negative. */
    public double getWeightChange() {
        if (records.size() < 2) {
            return 0;
        }
        return newest().getWeight() - records.get(0).getWeight();
    }

    /** Percentage toward the bound profile's FTP target, from 0 to 100. */
    public double getFTPGoalProgress() {
        return getFTPGoalProgress(profile);
    }

    /** Stage 1 signature: uses the supplied profile's current target. */
    public double getFTPGoalProgress(RiderProfile profile) {
        requireProfile(profile);
        double current = records.isEmpty() ? profile.getFtp() : newest().getFtp();
        double target = profile.getTargetFTP();
        if (current >= target) {
            return 100;
        }
        if (records.isEmpty() || target <= records.get(0).getFtp()) {
            return 0;
        }
        return percentage(records.get(0).getFtp(), current, target);
    }

    /** Percentage toward the bound profile's weight target, supporting loss or gain. */
    public double getWeightGoalProgress() {
        return getWeightGoalProgress(profile);
    }

    /** Stage 1 signature: uses the supplied profile's current target. */
    public double getWeightGoalProgress(RiderProfile profile) {
        requireProfile(profile);
        double current = records.isEmpty() ? profile.getWeight() : newest().getWeight();
        double target = profile.getTargetWeight();
        if (records.isEmpty()) {
            return current == target ? 100 : 0;
        }
        double baseline = records.get(0).getWeight();
        if (baseline == target) {
            return current == target ? 100 : 0;
        }
        return percentage(baseline, current, target);
    }

    /** Returns deterministic text suitable for a future controller to display. */
    public String getProgress() {
        double weight = records.isEmpty() ? profile.getWeight() : newest().getWeight();
        int ftp = records.isEmpty() ? profile.getFtp() : newest().getFtp();
        String history = records.size() < 2
                ? "Insufficient history for a trend." : "Changes from earliest record:";
        return String.format(Locale.ROOT,
                "FTP: %d W; weight: %.2f kg%n%s%nFTP change: %+d W; weight change: %+.2f kg"
                        + "%nFTP goal progress: %.2f%%; weight goal progress: %.2f%%",
                ftp, weight, history, getFTPChange(), getWeightChange(),
                getFTPGoalProgress(), getWeightGoalProgress());
    }

    private FitnessRecord newest() {
        return records.get(records.size() - 1);
    }

    private static double percentage(double baseline, double current, double target) {
        // Callers handle an equal baseline and target before reaching this calculation.
        double fraction = (current - baseline) / (target - baseline);
        return Math.max(0, Math.min(1, fraction)) * 100;
    }

    private static void requireProfile(RiderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required.");
        }
    }
}
