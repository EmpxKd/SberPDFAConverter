package by.eshed.pdfa.http;

import by.eshed.pdfa.validation.ValidationOutcome;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpResponses {

    private HttpResponses() {
    }

    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String json = "{\"error\":\"" + JsonWriter.escape(message == null ? "" : message) + "\"}";
        sendJson(exchange, status, json);
    }

    public static void sendValidationFailure(HttpExchange exchange, ValidationOutcome outcome) throws IOException {
        sendJson(exchange, 422, validationJson(outcome));
    }

    public static String validationJson(ValidationOutcome outcome) {
        return "{"
                + "\"compliant\":" + outcome.isCompliant() + ","
                + "\"flavour\":\"" + JsonWriter.escape(outcome.flavour().toString()) + "\","
                + "\"totalAssertions\":" + outcome.totalAssertions() + ","
                + "\"failedAssertions\":" + outcome.failedAssertions() + ","
                + "\"failures\":" + JsonWriter.stringArray(outcome.failureMessages())
                + "}";
    }
}
