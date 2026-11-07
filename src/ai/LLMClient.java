package ai;

import java.io.IOException;

/** Provider-independent text generation boundary for the Stage 1 AI services. */
public interface LLMClient {
    String sendPrompt(String prompt) throws IOException, InterruptedException;
}
