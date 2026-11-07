package ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DeepSeek chat-completion transport; contains no cycling or coaching logic. */
public class DeepSeekService {
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final String MODEL = "deepseek-flash";
    private final HttpClient client;
    private final String apiKey;

    /** Reads the process environment; does not load .env files or log credentials. */
    public DeepSeekService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), System.getenv("DEEPSEEK_API_KEY"));
    }

    // Package-private injection point for offline transport checks, not application configuration.
    DeepSeekService(HttpClient client, String apiKey) {
        this.client = Objects.requireNonNull(client, "client is required");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set the DEEPSEEK_API_KEY environment variable before using DeepSeek.");
        }
        this.apiKey = apiKey.strip();
        if (this.apiKey.chars().anyMatch(ch -> ch < 0x21 || ch > 0x7e)) {
            throw new IllegalStateException("DEEPSEEK_API_KEY contains invalid characters.");
        }
    }

    public String generateResponse(String prompt) throws IOException, InterruptedException {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be null or blank.");
        }
        String body = "{\"model\":" + quote(MODEL)
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + quote(prompt)
                + "}],\"stream\":false,\"thinking\":{\"type\":\"disabled\"},\"max_tokens\":4096}";
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw httpError(response.statusCode());
        }
        return assistantText(response.body());
    }

    private static IOException httpError(int status) {
        String detail = switch (status) {
            case 400, 422 -> "request rejected; check API configuration and prompt size";
            case 401, 403 -> "authentication/access failed; check DEEPSEEK_API_KEY and account permissions";
            case 402 -> "insufficient account balance";
            case 429 -> "rate limit reached; try again later";
            case 500, 502, 503, 504 -> "DeepSeek service unavailable; try again later";
            default -> "API request failed";
        };
        // Do not echo remote bodies, which may include credentials or user data.
        return new IOException("DeepSeek HTTP " + status + ": " + detail + ".");
    }

    private static String assistantText(String body) throws IOException {
        if (body == null || body.isBlank()) {
            throw new IOException("DeepSeek returned an empty response body.");
        }
        Object parsed = new Json(body).parse();
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IOException("Malformed DeepSeek response: expected a JSON object.");
        }
        if (root.containsKey("error")) {
            throw new IOException("DeepSeek returned an API error in its response.");
        }
        if (!(root.get("choices") instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || !"assistant".equals(message.get("role"))) {
            throw new IOException("Malformed DeepSeek response: missing assistant choice/message.");
        }
        if (!"stop".equals(choice.get("finish_reason"))) {
            throw new IOException("DeepSeek response did not finish normally (possibly truncated or filtered).");
        }
        Object content = message.get("content");
        if (content == null || content instanceof String text && text.isBlank()) {
            throw new IOException("DeepSeek returned empty assistant text.");
        }
        if (!(content instanceof String text)) {
            throw new IOException("Malformed DeepSeek response: assistant content must be text.");
        }
        return text;
    }

    private static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20 || Character.isSurrogate(ch)) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    /** Private JSON decoder: validates the entire document, not a content-matching regex. */
    private static final class Json {
        private static final Pattern NUMBER = Pattern.compile(
                "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
        private final String source;
        private int position;

        Json(String source) throws IOException {
            if (source.length() > 2_000_000) {
                throw new IOException("DeepSeek JSON response is too large.");
            }
            this.source = source;
        }

        Object parse() throws IOException {
            Object value = value(0);
            whitespace();
            if (position != source.length()) {
                throw invalid();
            }
            return value;
        }

        private Object value(int depth) throws IOException {
            whitespace();
            if (depth > 64 || position >= source.length()) {
                throw invalid();
            }
            char ch = source.charAt(position);
            if (ch == '"') {
                return string();
            }
            if (ch == '{') {
                position++;
                Map<String, Object> object = new HashMap<>();
                if (take('}')) {
                    return object;
                }
                do {
                    whitespace();
                    if (position >= source.length() || source.charAt(position) != '"') {
                        throw invalid();
                    }
                    String key = string();
                    expect(':');
                    if (object.containsKey(key)) {
                        throw invalid();
                    }
                    object.put(key, value(depth + 1));
                } while (take(','));
                expect('}');
                return object;
            }
            if (ch == '[') {
                position++;
                List<Object> array = new ArrayList<>();
                if (take(']')) {
                    return array;
                }
                do {
                    array.add(value(depth + 1));
                } while (take(','));
                expect(']');
                return array;
            }
            for (String literal : List.of("true", "false", "null")) {
                if (source.startsWith(literal, position)) {
                    position += literal.length();
                    return literal.equals("null") ? null : Boolean.valueOf(literal);
                }
            }
            Matcher number = NUMBER.matcher(source).region(position, source.length());
            if (number.lookingAt()) {
                position = number.end();
                try {
                    return new BigDecimal(number.group());
                } catch (NumberFormatException exception) {
                    throw invalid();
                }
            }
            throw invalid();
        }

        private String string() throws IOException {
            position++; // Opening quote has already been checked.
            StringBuilder text = new StringBuilder();
            while (position < source.length()) {
                char ch = source.charAt(position++);
                if (ch == '"') {
                    return text.toString();
                }
                if (ch < 0x20) {
                    throw invalid();
                }
                if (ch != '\\') {
                    text.append(ch);
                    continue;
                }
                if (position >= source.length()) {
                    throw invalid();
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"', '\\', '/' -> text.append(escape);
                    case 'b' -> text.append('\b');
                    case 'f' -> text.append('\f');
                    case 'n' -> text.append('\n');
                    case 'r' -> text.append('\r');
                    case 't' -> text.append('\t');
                    case 'u' -> {
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            if (position >= source.length()) {
                                throw invalid();
                            }
                            char digit = source.charAt(position++);
                            int hex = "0123456789abcdef".indexOf(Character.toLowerCase(digit));
                            if (hex < 0) {
                                throw invalid();
                            }
                            code = code * 16 + hex;
                        }
                        text.append((char) code);
                    }
                    default -> throw invalid();
                }
            }
            throw invalid();
        }

        private boolean take(char ch) {
            whitespace();
            if (position < source.length() && source.charAt(position) == ch) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char ch) throws IOException {
            if (!take(ch)) {
                throw invalid();
            }
        }

        private void whitespace() {
            while (position < source.length() && " \t\r\n".indexOf(source.charAt(position)) >= 0) {
                position++;
            }
        }

        private IOException invalid() {
            return new IOException("Malformed DeepSeek JSON response at character " + position + ".");
        }
    }
}
