package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.validation.ValidationOutcome;

/**
 * Сбой конвейера конвертации. Если причина — провал обязательного гейта veraPDF
 * (DECISIONS.md п.6: "конвертер обязан гарантировать соответствие"), {@link #validationOutcome()}
 * содержит список нарушенных правил для диагностики на стороне СХЭД.
 */
public final class PdfAConversionException extends RuntimeException {

    private final ValidationOutcome validationOutcome;

    public PdfAConversionException(String message, Throwable cause) {
        super(message, cause);
        this.validationOutcome = null;
    }

    public PdfAConversionException(String message, ValidationOutcome validationOutcome) {
        super(message);
        this.validationOutcome = validationOutcome;
    }

    public ValidationOutcome validationOutcome() {
        return validationOutcome;
    }
}
