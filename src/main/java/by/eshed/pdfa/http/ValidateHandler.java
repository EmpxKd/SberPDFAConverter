package by.eshed.pdfa.http;

import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.validation.ValidationOutcome;
import by.eshed.pdfa.validation.VeraPdfValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST /api/v1/validate?flavour=1b - тело запроса - произвольный PDF, проверка через veraPDF.
 * Нужен и для приёма документов от систем-источников (DECISIONS.md: "привязка к источнику/сроку
 * хранения"), и для ручной проверки результата конвертера. Флавор валидации ограничен PDF/A-1
 * ({@code 1a}/{@code 1b}, дефолт 1b) тем же {@link PdfAFlavourOption#parse} — конвертер фиксирован
 * на части 1 стандарта (PLAN.md), валидация входящих PDF проверяется на соответствие ей же.
 */
public final class ValidateHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(ValidateHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.sendError(exchange, 405, "Только POST");
            return;
        }
        try {
            PdfAFlavourOption flavour = PdfAFlavourOption.parse(queryParam(exchange, "flavour"));
            byte[] pdfBytes = exchange.getRequestBody().readAllBytes();
            if (pdfBytes.length == 0) {
                HttpResponses.sendError(exchange, 400, "Пустое тело запроса - ожидается PDF");
                return;
            }
            ValidationOutcome outcome = VeraPdfValidator.validate(pdfBytes, flavour.veraPdfFlavour());
            HttpResponses.sendJson(exchange, 200, HttpResponses.validationJson(outcome));
        } catch (IllegalArgumentException badInput) {
            HttpResponses.sendError(exchange, 400, badInput.getMessage());
        } catch (IOException validationFailure) {
            LOG.log(Level.WARNING, "Ошибка прогона veraPDF", validationFailure);
            HttpResponses.sendError(exchange, 500, validationFailure.getMessage());
        }
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
