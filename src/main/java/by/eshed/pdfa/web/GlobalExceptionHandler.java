package by.eshed.pdfa.web;

import by.eshed.pdfa.batch.BatchDocumentNotReadyException;
import by.eshed.pdfa.batch.BatchNotFoundException;
import by.eshed.pdfa.batch.BatchQueueFullException;
import by.eshed.pdfa.pipeline.PdfAConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Единая обработка ошибок REST-слоя — заменяет ручную сборку JSON-ответов из старого
 * {@code http.HttpResponses}/{@code http.JsonWriter}. Семантика статусов сохранена буквально:
 * некорректный вход — 400, провал обязательного гейта veraPDF — 422 с отчётом, прочий сбой
 * конвейера и любая необработанная ошибка — 500. Батч-эндпоинты добавляют: неизвестный
 * {@code jobId}/{@code docId} — 404, документ батча ещё не готов — 409, переполненная очередь
 * воркеров — 429 с {@code Retry-After}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(IllegalArgumentException ex) {
        LOG.info("Некорректный вход: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBatchNotFound(BatchNotFoundException ex) {
        LOG.info("Батч/документ не найден: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BatchDocumentNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleBatchDocumentNotReady(BatchDocumentNotReadyException ex) {
        LOG.info("Документ батча ещё не готов: {}", ex.getMessage());
        return ResponseEntity.status(409).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BatchQueueFullException.class)
    public ResponseEntity<ErrorResponse> handleBatchQueueFull(BatchQueueFullException ex) {
        LOG.warn("Очередь воркеров батча переполнена: {}", ex.getMessage());
        return ResponseEntity.status(429)
                .header("Retry-After", "5")
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(PdfAConversionException.class)
    public ResponseEntity<?> handleConversionFailure(PdfAConversionException ex) {
        if (ex.validationOutcome() != null) {
            return handleValidationGateFailure(ex);
        }
        return handlePipelineFailure(ex);
    }

    private ResponseEntity<ValidationReport> handleValidationGateFailure(PdfAConversionException ex) {
        LOG.warn("Результат не прошёл обязательную проверку veraPDF: {}", ex.getMessage());
        return ResponseEntity.status(422).body(ValidationReport.of(ex.validationOutcome()));
    }

    private ResponseEntity<ErrorResponse> handlePipelineFailure(PdfAConversionException ex) {
        LOG.error("Сбой конвертации", ex);
        return ResponseEntity.status(500).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        LOG.error("Необработанная ошибка", ex);
        return ResponseEntity.status(500).body(new ErrorResponse("Внутренняя ошибка сервера"));
    }
}
