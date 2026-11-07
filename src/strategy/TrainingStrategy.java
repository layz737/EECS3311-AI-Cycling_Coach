package strategy;

/** Supplies goal-specific instructions, independently of rider data and provider. */
public interface TrainingStrategy {
    String buildPrompt();
}
