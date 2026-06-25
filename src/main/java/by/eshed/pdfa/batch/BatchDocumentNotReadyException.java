package by.eshed.pdfa.batch;

/**
 * Запрошено скачивание документа, который существует в батче, но ещё не {@code DONE}.
 * Перехватывается {@code GlobalExceptionHandler} и превращается в {@code 409 Conflict}.
 */
public final class BatchDocumentNotReadyException extends RuntimeException {

    public BatchDocumentNotReadyException(String message) {
        super(message);
    }
}
