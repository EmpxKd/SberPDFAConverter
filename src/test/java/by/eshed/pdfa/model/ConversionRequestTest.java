package by.eshed.pdfa.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, задача 2: дефолт {@code PDF_A_1B} и отказ при вложении подписи (часть 1 стандарта
 * запрещает {@code /EmbeddedFiles} .
 */
class ConversionRequestTest {

    private static PageSource page() {
        return new PageSource(new byte[]{1, 2, 3}, SourceFormat.PNG);
    }

    @Test
    void defaultsToPdfA1bWhenFlavourNotSet() {
        ConversionRequest request = ConversionRequest.builder().pages(List.of(page())).build();
        assertEquals(PdfAFlavourOption.PDF_A_1B, request.flavour());
    }

    @Test
    void defaultsToStrictValidationEnabled() {
        ConversionRequest request = ConversionRequest.builder().pages(List.of(page())).build();
        assertTrue(request.strictValidation());
    }

    @Test
    void rejectsEmptyPageList() {
        assertThrows(IllegalArgumentException.class, () -> ConversionRequest.builder().pages(List.of()).build());
    }

    @Test
    void rejectsNullPageList() {
        assertThrows(NullPointerException.class, () -> ConversionRequest.builder().pages(null));
    }
}