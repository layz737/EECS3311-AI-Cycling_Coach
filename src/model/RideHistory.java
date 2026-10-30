package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Collection of completed rides from the Stage 1 design. */
public class RideHistory {
    private final List<Ride> rides = new ArrayList<>();

    /**
     * Adds a ride in insertion order; duplicate entries are allowed.
     *
     * @throws IllegalArgumentException if the ride is null
     */
    public void addRide(Ride ride) {
        if (ride == null) {
            throw new IllegalArgumentException("ride is required.");
        }
        rides.add(ride);
    }

    /** Returns an unmodifiable snapshot of all rides in insertion order. */
    public List<Ride> getRides() {
        return List.copyOf(rides);
    }

    /**
     * Returns an unmodifiable snapshot of rides from today and the preceding six
     * days, inclusive, in insertion order. Uses the system default timezone and
     * excludes future rides.
     */
    public List<Ride> getRecentRides() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(6);
        List<Ride> recentRides = new ArrayList<>();
        for (Ride ride : rides) {
            LocalDate date = ride.getDate();
            if (!date.isBefore(firstDay) && !date.isAfter(today)) {
                recentRides.add(ride);
            }
        }
        return List.copyOf(recentRides);
    }
}
