package service;

import ai.LLMClient;
import model.RideHistory;
import model.RiderProfile;
import model.TrainingPlan;
import strategy.FTPTrainingStrategy;
import strategy.GeneralTrainingStrategy;
import strategy.TrainingStrategy;
import strategy.WeightTrainingStrategy;

import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stage 1 F04 pipeline: goal strategy, rider context, LLM, then strict plan validation. */
public class TrainingPlanGenerator {
    private static final Pattern FTP_GOAL = Pattern.compile("\\b(?:ftp|power|threshold)\\b");
    private static final Pattern WEIGHT_GOAL = Pattern.compile("\\b(?:weight|fat\\s+loss)\\b");
    private final LLMClient llmClient;
    private final RiderContextService contextService;
    private final RiderProfile profile;
    private final RideHistory history;
    private final TrainingPlanParser parser;
    private final Clock clock;

    public TrainingPlanGenerator(LLMClient llmClient, RiderContextService contextService,
                                 RiderProfile profile, RideHistory history, TrainingPlanParser parser) {
        this(llmClient, contextService, profile, history, parser, Clock.systemDefaultZone());
    }

    /** Clock injection makes the requested Monday-Sunday week reproducible in checks. */
    public TrainingPlanGenerator(LLMClient llmClient, RiderContextService contextService,
                                 RiderProfile profile, RideHistory history, TrainingPlanParser parser, Clock clock) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient is required");
        this.contextService = Objects.requireNonNull(contextService, "contextService is required");
        this.profile = Objects.requireNonNull(profile, "profile is required");
        this.history = Objects.requireNonNull(history, "history is required");
        this.parser = Objects.requireNonNull(parser, "parser is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Returns a new validated plan without replacing any existing plan.
     * Requires at least one recent ride, following F04's missing-history error rule.
     * Malformed/infeasible output throws IllegalArgumentException; API errors propagate.
     */
    public TrainingPlan generatePlan() throws IOException, InterruptedException {
        if (history.getRecentRides().isEmpty()) {
            throw new IllegalStateException("Weekly plan generation requires at least one ride"
                    + " from today and the preceding six days.");
        }
        LocalDate monday = LocalDate.now(clock).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        int availableDays = profile.getAvailableTrainingDays();
        TrainingStrategy strategy = selectStrategy(profile.getMainGoal());
        String prompt = "You are a cycling training assistant generating a feasible weekly plan.\n"
                + strategy.buildPrompt() + "\nRider context:\n"
                + contextService.buildContext(profile, history) + "\n"
                + "Do not invent rider measurements or assume detailed interval/time-series history.\n"
                + "Return exactly one week: " + monday + " through " + monday.plusDays(6) + ".\n"
                + "Schedule at most " + availableDays + " active training days; other days must be Rest.\n"
                + "The available-days value is a count, not specific weekday availability.\n"
                + "Use this strict output format only, with no Markdown fences, headings, or commentary:\n"
                + "First line: PLAN\nThen exactly seven daily rows in consecutive Monday-Sunday order:\n"
                + "date|type|durationMinutes|targetPower|description\n"
                + "Last line: END\nDo not include a column-header row.\n"
                + "Dates must be ISO yyyy-MM-dd. Each row has exactly five non-blank fields.\n"
                + "Type and description must not contain pipes or line breaks.\n"
                + "Active days: positive finite decimal minutes and non-negative integer watts.\n"
                + "Rest days: use exactly type Rest, durationMinutes 0, targetPower 0, and a description.\n"
                + "No more than one row per date; every date must be included.\n";
        String response = llmClient.sendPrompt(prompt);
        TrainingPlan plan = parser.parsePlan(response, monday);
        if (plan.getWorkouts().size() > availableDays) {
            throw new IllegalArgumentException("Generated plan exceeds the rider's "
                    + availableDays + " available training days.");
        }
        return plan;
    }

    private static TrainingStrategy selectStrategy(String mainGoal) {
        String goal = mainGoal.toLowerCase(Locale.ROOT);
        // FTP keywords take precedence when a goal mentions both categories.
        if (FTP_GOAL.matcher(goal).find()) {
            return new FTPTrainingStrategy();
        }
        if (WEIGHT_GOAL.matcher(goal).find()) {
            return new WeightTrainingStrategy();
        }
        return new GeneralTrainingStrategy();
    }
}
