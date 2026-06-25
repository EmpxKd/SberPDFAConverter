package by.eshed.pdfa.web;

import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.validation.ValidationOutcome;
import by.eshed.pdfa.validation.VeraPdfValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * {@code POST /api/v1/validate?flavour=1a|1b} — тело запроса: произвольный PDF целиком (не
 * form-data), проверка через veraPDF. Флейвор валидации ограничен PDF/A-1, как и конвертер
 * (см. {@link PdfAFlavourOption#parse}).
 *
 * <p>{@code flavour} парсится из {@link HttpServletRequest#getQueryString()} вручную, а тело
 * читается напрямую через {@link HttpServletRequest#getInputStream()} — байты сокета, как старый
 * {@code http.ValidateHandler}. Раньше тут стояли {@code @RequestParam("flavour")} +
 * {@code @RequestBody byte[]}: при {@code Content-Type: application/x-www-form-urlencoded} (то,
 * что curl/Postman ставят по умолчанию без явного заголовка) сам резолв {@code @RequestParam}
 * вызывает {@code HttpServletRequest.getParameter(...)}, а контракт Servlet API требует, чтобы
 * этот вызов вычитал и распарсил всё тело запроса как form-параметры — независимо от того, что
 * искомый параметр и так был в query string. К моменту резолва {@code @RequestBody} поток уже
 * пуст. Простая замена {@code @RequestBody} на ручной {@code getInputStream()} не помогает, пока
 * рядом остаётся {@code @RequestParam}: тело всё равно вытирается на шаге резолва аргументов
 * (до начала тела метода) — подтверждено руками (`curl --data-binary` без {@code Content-Type}
 * → тело читалось как 0 байт). Решение: не дёргать {@code getParameter()}/{@code getParameterMap()}
 * вовсе — тогда контейнер не имеет повода трогать тело, и {@code getInputStream()} отдаёт его
 * целиком независимо от заголовков.</p>
 */
@RestController
public class ValidateController {

    private static final Logger LOG = LoggerFactory.getLogger(ValidateController.class);

    /**
     * Проверяет переданный PDF на соответствие указанному флейвору PDF/A-1.
     *
     * @return JSON-отчёт veraPDF
     * @throws IOException              при ошибке прогона veraPDF
     * @throws IllegalArgumentException на пустое тело запроса или неподдерживаемый флейвор
     */
    @PostMapping("/api/v1/validate")
    public ResponseEntity<ValidationReport> validate(HttpServletRequest request) throws IOException {
        String flavour = extractFlavourFromQueryString(request.getQueryString());
        byte[] pdfBytes = request.getInputStream().readAllBytes();
        LOG.info("Вход: validate, flavour={}, bytes={}", flavour, pdfBytes.length);
        if (pdfBytes.length == 0) {
            throw new IllegalArgumentException("Пустое тело запроса - ожидается PDF");
        }
        PdfAFlavourOption parsedFlavour = PdfAFlavourOption.parse(flavour);
        ValidationOutcome outcome = VeraPdfValidator.validate(pdfBytes, parsedFlavour.veraPdfFlavour());
        LOG.info("Выход: validate, compliant={}", outcome.isCompliant());
        return ResponseEntity.ok(ValidationReport.of(outcome));
    }

    /**
     * Достаёт {@code flavour} из сырой query string без обращения к
     * {@code HttpServletRequest.getParameter(...)} — этот вызов на POST с
     * {@code Content-Type: application/x-www-form-urlencoded} вычитывает тело запроса целиком,
     * что для этого эндпойнта недопустимо (тело — сам PDF, не form-данные).
     */
    private static String extractFlavourFromQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return null;
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!"flavour".equals(key)) {
                continue;
            }
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }
}
