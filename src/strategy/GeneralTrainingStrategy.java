package strategy;

public class GeneralTrainingStrategy implements TrainingStrategy {
    @Override
    public String buildPrompt() {
        return """
                Goal focus: balanced cycling fitness and aerobic endurance.
                Prioritize consistency, a sensible intensity distribution, and sufficient recovery.
                Keep the workload appropriate to the supplied fitness and recent history,
                and respect available training days.
                """;
    }
}
