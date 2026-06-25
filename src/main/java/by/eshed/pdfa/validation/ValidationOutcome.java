package by.eshed.pdfa.validation;

import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.util.List;

/**
 * Результат прогона veraPDF — обязательного гейта конвертера: выход должен проходить veraPDF
 * как валидный PDF/A без ошибок.
 */
public final class ValidationOutcome {

    private final boolean compliant;
    private final PDFAFlavour flavour;
    private final int totalAssertions;
    private final int failedAssertions;
    private final List<String> failureMessages;

    public ValidationOutcome(boolean compliant, PDFAFlavour flavour, int totalAssertions,
                              int failedAssertions, List<String> failureMessages) {
        this.compliant = compliant;
        this.flavour = flavour;
        this.totalAssertions = totalAssertions;
        this.failedAssertions = failedAssertions;
        this.failureMessages = List.copyOf(failureMessages);
    }

    public boolean isCompliant() {
        return compliant;
    }

    public PDFAFlavour flavour() {
        return flavour;
    }

    public int totalAssertions() {
        return totalAssertions;
    }

    public int failedAssertions() {
        return failedAssertions;
    }

    public List<String> failureMessages() {
        return failureMessages;
    }
}
