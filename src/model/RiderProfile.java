package model;

/**
 * Rider measurements, availability, and goals for Stage 1 feature F01.
 * Observer support and relationships to other model classes are deferred.
 */
public class RiderProfile {
    private double weight;
    private int ftp;
    private int maxHR;
    private int restingHR;
    private int availableTrainingDays;
    private double targetWeight;
    private int targetFTP;
    private String mainGoal;

    /**
     * Creates a complete, valid profile.
     *
     * @throws IllegalArgumentException if any value is invalid or the goal is missing
     */
    public RiderProfile(double weight, int ftp, int maxHR, int restingHR,
                        int availableTrainingDays, double targetWeight,
                        int targetFTP, String mainGoal) {
        updateProfile(weight, ftp, maxHR, restingHR, availableTrainingDays,
                targetWeight, targetFTP, mainGoal);
    }

    /**
     * Replaces the profile values only after all inputs pass validation.
     * A rejected update leaves the previous profile unchanged.
     *
     * @throws IllegalArgumentException if any value is invalid or the goal is missing
     */
    public final void updateProfile(double weight, int ftp, int maxHR, int restingHR,
                                    int availableTrainingDays, double targetWeight,
                                    int targetFTP, String mainGoal) {
        requirePositiveFinite(weight, "weight");
        requirePositive(ftp, "ftp");
        requirePositive(maxHR, "maxHR");
        requirePositive(restingHR, "restingHR");
        if (restingHR >= maxHR) {
            throw new IllegalArgumentException("restingHR must be less than maxHR.");
        }
        if (availableTrainingDays < 1 || availableTrainingDays > 7) {
            throw new IllegalArgumentException("availableTrainingDays must be between 1 and 7.");
        }
        requirePositiveFinite(targetWeight, "targetWeight");
        requirePositive(targetFTP, "targetFTP");
        if (mainGoal == null || mainGoal.isBlank()) {
            throw new IllegalArgumentException("mainGoal is required.");
        }

        this.weight = weight;
        this.ftp = ftp;
        this.maxHR = maxHR;
        this.restingHR = restingHR;
        this.availableTrainingDays = availableTrainingDays;
        this.targetWeight = targetWeight;
        this.targetFTP = targetFTP;
        this.mainGoal = mainGoal.strip();
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be a finite positive number.");
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    public double getWeight() {
        return weight;
    }

    public int getFtp() {
        return ftp;
    }

    public int getMaxHR() {
        return maxHR;
    }

    public int getRestingHR() {
        return restingHR;
    }

    public int getAvailableTrainingDays() {
        return availableTrainingDays;
    }

    public double getTargetWeight() {
        return targetWeight;
    }

    public int getTargetFTP() {
        return targetFTP;
    }

    public String getMainGoal() {
        return mainGoal;
    }
}
