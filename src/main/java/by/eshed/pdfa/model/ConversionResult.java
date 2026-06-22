package by.eshed.pdfa.model;

import by.eshed.pdfa.validation.ValidationOutcome;

import java.util.Objects;

public final class ConversionResult {

    private final byte[] pdfBytes;
    private final PdfAFlavourOption flavour;
    private final ValidationOutcome validation;

    public ConversionResult(byte[] pdfBytes, PdfAFlavourOption flavour, ValidationOutcome validation) {
        this.pdfBytes = Objects.requireNonNull(pdfBytes, "pdfBytes");
        this.flavour = Objects.requireNonNull(flavour, "flavour");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    public byte[] pdfBytes() {
        return pdfBytes;
    }

    public PdfAFlavourOption flavour() {
        return flavour;
    }

    public ValidationOutcome validation() {
        return validation;
    }
}
