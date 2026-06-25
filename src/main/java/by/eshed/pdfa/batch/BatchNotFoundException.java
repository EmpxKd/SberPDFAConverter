package by.eshed.pdfa.batch;

/**
 * Запрошен неизвестный {@code jobId} или {@code docId} внутри известного {@code jobId}.
 * Перехватывается {@code GlobalExceptionHandler} и превращается в {@code 404}.
 */
public final class BatchNotFoundException extends RuntimeException {

    public BatchNotFoundException(String message) {
        super(message);
    }
}
