package by.eshed.pdfa.validation;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraPDFFoundry;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;
import org.verapdf.core.VeraPDFException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Обязательный гейт конвертера: PDFBox сам по себе не гарантирует соответствие PDF/A, гарантию
 * даёт только прогон через veraPDF - тот же валидатор, что используют архивные учреждения.
 *
 * Использует Greenfield-движок veraPDF (org.verapdf:validation-model) и вызывается прямо из этого
 * процесса. В отличие от PDFBox-движка (org.verapdf:pdfbox-validation-model) у Greenfield нет
 * собственного форка Apache PDFBox: коллизия классов с org.apache.pdfbox:pdfbox:3.0.7, на котором
 * работает весь остальной код, физически невозможна, поэтому отдельный JVM-подпроцесс не нужен.
 */
public final class VeraPdfValidator {

    private static volatile boolean foundryInitialised = false;

    private VeraPdfValidator() {
    }

    public static synchronized ValidationOutcome validate(byte[] pdfBytes, PDFAFlavour flavour) throws IOException {
        ensureFoundryInitialised();

        VeraPDFFoundry foundry = Foundries.defaultInstance();
        try (PDFAParser parser = foundry.createParser(new ByteArrayInputStream(pdfBytes), flavour)) {
            PDFAValidator validator = foundry.createValidator(flavour, false);
            ValidationResult result = validator.validate(parser);
            return toOutcome(result, flavour);
        } catch (VeraPDFException e) {
            throw new IOException("Ошибка прогона veraPDF (Greenfield)", e);
        }
    }

    private static void ensureFoundryInitialised() {
        if (!foundryInitialised) {
            VeraGreenfieldFoundryProvider.initialise();
            foundryInitialised = true;
        }
    }

    private static ValidationOutcome toOutcome(ValidationResult result, PDFAFlavour flavour) {
        List<String> failures = new ArrayList<>();
        result.getTestAssertions().stream()
                .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                .map(a -> a.getRuleId() + ": " + a.getMessage()
                        + " | location=" + a.getLocationContext() + " | error=" + a.getErrorMessage())
                .forEach(failures::add);
        return new ValidationOutcome(result.isCompliant(), flavour, result.getTotalAssertions(),
                failures.size(), failures);
    }
}