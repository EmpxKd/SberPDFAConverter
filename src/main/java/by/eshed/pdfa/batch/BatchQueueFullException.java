package by.eshed.pdfa.batch;

/**
 * Очередь воркеров пула не может вместить весь батч. Перехватывается {@code GlobalExceptionHandler}
 * и превращается в {@code 429 Too Many Requests} с заголовком {@code Retry-After} - клиент должен
 * повторить запрос позже, батч целиком не регистрируется (без частичной постановки части
 * документов в очередь).
 */
public final class BatchQueueFullException extends RuntimeException {

    public BatchQueueFullException(String message) {
        super(message);
    }
}
