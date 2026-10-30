package model;

import java.time.LocalDate;

/** Immutable dated snapshot of FTP in watts and rider weight in kilograms. */
public class FitnessRecord {
    private final LocalDate date;
    private final int ftp;
    private final double weight;

    /**
     * Creates a fitness snapshot from the Stage 1 design.
     *
     * @throws IllegalArgumentException if the date is missing, FTP is not positive,
     *                                  or weight is not finite and positive
     */
    public FitnessRecord(LocalDate date, int ftp, double weight) {
        if (date == null) {
            throw new IllegalArgumentException("date is required.");
        }
        if (ftp <= 0) {
            throw new IllegalArgumentException("ftp must be positive.");
        }
        if (!Double.isFinite(weight) || weight <= 0) {
            throw new IllegalArgumentException("weight must be a finite positive number.");
        }
        this.date = date;
        this.ftp = ftp;
        this.weight = weight;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getFtp() {
        return ftp;
    }

    public double getWeight() {
        return weight;
    }
}
