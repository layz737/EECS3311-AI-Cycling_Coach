package strategy;

public class WeightTrainingStrategy implements TrainingStrategy {
    @Override
    public String buildPrompt() {
        return """
                Goal focus: support sustainable weight reduction while maintaining training quality.
                Favor manageable aerobic volume, consistency, and adequate recovery within available
                training days. Avoid unsafe or extreme calorie-loss instructions, crash dieting,
                or compensatory exercise. Do not provide medical advice or promise weight loss.
                """;
    }
}
