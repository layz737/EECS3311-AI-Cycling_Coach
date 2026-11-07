package ai;

import java.io.IOException;
import java.util.Objects;

/** Adapts DeepSeek's service to the provider-independent LLMClient interface. */
public class DeepSeekAdapter implements LLMClient {
    private final DeepSeekService service;

    public DeepSeekAdapter(DeepSeekService service) {
        this.service = Objects.requireNonNull(service, "service is required");
    }

    @Override
    public String sendPrompt(String prompt) throws IOException, InterruptedException {
        return service.generateResponse(prompt);
    }
}
