package service;

import model.Ride;
import model.RideHistory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Stage 1 F11 personal bests from today.minusMonths(1) through today, inclusive.
 * Future rides are excluded; ties keep the first ride in history order.
 * Queries return Optional.empty() when no eligible rides exist and reject null histories.
 */
public class PersonalBestService {
    private final Clock clock;

    /** Uses today's date in the system default timezone. */
    public PersonalBestService() {
        this(Clock.systemDefaultZone());
    }

    /** Allows a fixed clock for reproducible date-window checks. */
    public PersonalBestService(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required.");
        }
        this.clock = clock;
    }

    public Optional<Ride> getLongestRide(RideHistory history) {
        return findBest(history, Ride::getDistance);
    }

    public Optional<Ride> getHighestAvgPower(RideHistory history) {
        return findBest(history, Ride::getAvgPower);
    }

    public Optional<Ride> getHighestMaxPower(RideHistory history) {
        return findBest(history, Ride::getMaxPower);
    }

    public Optional<Ride> getFastestAvgSpeed(RideHistory history) {
        return findBest(history, Ride::getAvgSpeed);
    }

    /**
     * Includes dates from today.minusMonths(1) through today, inclusive.
     * Future rides are excluded. Ties retain the first ride in history order.
     * Empty histories and periods return Optional.empty().
     */
    private Optional<Ride> findBest(RideHistory history, ToDoubleFunction<Ride> metric) {
        if (history == null) {
            throw new IllegalArgumentException("history is required.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusMonths(1);
        Ride best = null;
        for (Ride ride : history.getRides()) {
            LocalDate date = ride.getDate();
            if (!date.isBefore(firstDay) && !date.isAfter(today)
                    && (best == null || metric.applyAsDouble(ride) > metric.applyAsDouble(best))) {
                best = ride;
            }
        }
        return Optional.ofNullable(best);
    }
}
