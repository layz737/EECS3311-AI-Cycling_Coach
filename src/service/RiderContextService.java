package service;

import model.Ride;
import model.RideHistory;
import model.RiderProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Prepares rider data for prompts without making coaching judgments. */
public class RiderContextService {
    private static final int MAX_RECENT_RIDES = 10;
    private static final int MAX_GOAL_LENGTH = 1000;

    /** Includes at most ten rides from the history's seven-day window, newest date first. */
    public String buildContext(RiderProfile profile, RideHistory history) {
        requireProfile(profile);
        if (history == null) {
            throw new IllegalArgumentException("history is required.");
        }
        List<Ride> recent = history.getRecentRides().stream()
                .sorted(Comparator.comparing(Ride::getDate).reversed()).toList();
        StringBuilder context = new StringBuilder(profileContext(profile));
        context.append("\nRecent rides (today and preceding six days; newest date first):\n");
        if (recent.isEmpty()) {
            context.append("No rides recorded in this period.\n");
        } else {
            int count = Math.min(MAX_RECENT_RIDES, recent.size());
            context.append("Included ").append(count).append(" of ").append(recent.size()).append(" recent rides.\n");
            for (int i = 0; i < count; i++) {
                context.append("Ride ").append(i + 1).append(":\n").append(rideContext(recent.get(i)));
            }
        }
        return context.toString();
    }

    public String buildRideContext(RiderProfile profile, Ride ride) {
        requireProfile(profile);
        if (ride == null) {
            throw new IllegalArgumentException("ride is required.");
        }
        return profileContext(profile) + "\nSelected ride:\n" + rideContext(ride);
    }

    private static String profileContext(RiderProfile profile) {
        String goal = profile.getMainGoal();
        if (goal.length() > MAX_GOAL_LENGTH) {
            goal = goal.substring(0, MAX_GOAL_LENGTH) + " [truncated]";
        }
        // Keep multiline free text indented within the goal field.
        goal = goal.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\n  ");
        return String.format(Locale.ROOT,
                "Rider profile:\nWeight: %s kg\nFTP: %d W\nMaximum HR: %d bpm\nResting HR: %d bpm"
                        + "\nTarget weight: %s kg\nTarget FTP: %d W\nAvailable training days per week: %d\nMain goal: %s\n",
                profile.getWeight(), profile.getFtp(), profile.getMaxHR(), profile.getRestingHR(),
                profile.getTargetWeight(), profile.getTargetFTP(), profile.getAvailableTrainingDays(), goal);
    }

    private static String rideContext(Ride ride) {
        return String.format(Locale.ROOT,
                "  Date: %s\n  Distance: %s km\n  Duration: %s s\n  Average speed: %s km/h"
                        + "\n  Average HR: %d bpm\n  Maximum HR: %d bpm\n  Average power: %d W"
                        + "\n  Maximum power: %d W\n  Average cadence: %d rpm\n",
                ride.getDate(), ride.getDistance(), ride.getDuration(), ride.getAvgSpeed(),
                ride.getAvgHR(), ride.getMaxHR(), ride.getAvgPower(), ride.getMaxPower(), ride.getAvgCadence());
    }

    private static void requireProfile(RiderProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required.");
        }
    }
}
