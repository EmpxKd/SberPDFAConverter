package by.eshed.pdfa.http;

import by.eshed.pdfa.ocr.OcrEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * GET /healthz - сообщает в т.ч. доступность Tesseract: OCR обязателен на каждой конвертации
 * (DECISIONS.md п.2), поэтому его недоступность - это деградация сервиса, а не мелочь.
 */
public final class HealthHandler implements HttpHandler {

    private final OcrEngine ocrEngine;

    public HealthHandler(OcrEngine ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.sendError(exchange, 405, "Только GET");
            return;
        }
        boolean tesseractAvailable = ocrEngine.isAvailable();
        String json = "{\"status\":\"ok\",\"tesseractAvailable\":" + tesseractAvailable + "}";
        HttpResponses.sendJson(exchange, tesseractAvailable ? 200 : 503, json);
    }
}
