package strategy;

public class FTPTrainingStrategy implements TrainingStrategy {
    @Override
    public String buildPrompt() {
        return """
                Goal focus: improve FTP and sustainable cycling power.
                Use structured aerobic, sweet spot, threshold, or VO2 work only where appropriate
                to the supplied fitness and recent history. Do not overprescribe intensity or
                schedule demanding sessions without adequate recovery. Respect available training days.
                """;
    }
}
