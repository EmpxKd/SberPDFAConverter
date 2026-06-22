package by.eshed.pdfa.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PLAN.md, "Жёсткая фиксация конвертера на PDF/A-1": конвертер достижим только на {@code 1a}/{@code 1b}.
 * Заменяет прежние тесты на дефолт PDF/A-2b и парсинг "3b" - то поведение отменено решением заказчика.
 */
class PdfAFlavourOptionTest {

    @ParameterizedTest
    @NullAndEmptySource
    void defaultsToPdfA1bWhenBlank(String value) {
        assertEquals(PdfAFlavourOption.PDF_A_1B, PdfAFlavourOption.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1b", "1B", "pdf-a-1b", "PDF/A-1b", "PDFA-1B", "pdf_a_1b"})
    void parsesExplicit1bNotations(String value) {
        assertEquals(PdfAFlavourOption.PDF_A_1B, PdfAFlavourOption.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1a", "1A", "pdf-a-1a", "PDF/A-1a", "PDFA-1A"})
    void parsesExplicit1aNotations(String value) {
        assertEquals(PdfAFlavourOption.PDF_A_1A, PdfAFlavourOption.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2b", "2B", "3b", "3B", "2a", "3a", "pdf-a-2b"})
    void rejectsNonPart1Profiles(String value) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PdfAFlavourOption.parse(value));
        assertEquals("Поддерживается только PDF/A-1 (1a или 1b): " + value, ex.getMessage());
    }

    @Test
    void rejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> PdfAFlavourOption.parse("9x"));
    }

    @Test
    void partAndConformanceMatchStandardForBothLevels() {
        assertEquals(1, PdfAFlavourOption.PDF_A_1A.part());
        assertEquals("A", PdfAFlavourOption.PDF_A_1A.conformance());
        assertEquals(1, PdfAFlavourOption.PDF_A_1B.part());
        assertEquals("B", PdfAFlavourOption.PDF_A_1B.conformance());
    }
}