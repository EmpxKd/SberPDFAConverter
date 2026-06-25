package by.eshed.pdfa.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /healthz} — контракт health-check (отдельно от {@code /actuator/health}),
 * который уже знают клиенты СХЭД/Postman-коллекции. Отражает только то, что процесс поднялся.
 */
@RestController
public class HealthController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);

    /**
     * @return {@code 200} всегда
     */
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        LOG.info("Вход/выход: health");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        return ResponseEntity.ok(body);
    }
}
