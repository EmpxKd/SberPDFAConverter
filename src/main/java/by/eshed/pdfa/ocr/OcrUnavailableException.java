package by.eshed.pdfa.ocr;

/**
 * Tesseract не установлен/не найден в окружении. Нативная зависимость допустима по
 * DECISIONS.md п.9, но это не pure-Java часть стека — отдельный класс исключения, чтобы
 * конвейер мог явно сообщить о причине сбоя, а не тонуть в обобщённом RuntimeException из JNA.
 */
public final class OcrUnavailableException extends RuntimeException {

    public OcrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
