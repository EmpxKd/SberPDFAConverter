package by.eshed.pdfa.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfAFlavourOptionTest {

    @Test
    void defaultsToPdfA2bWhenBlank() {
        assertEquals(PdfAFlavourOption.PDF_A_2B, PdfAFlavourOption.parse(null));
        assertEquals(PdfAFlavourOption.PDF_A_2B, PdfAFlavourOption.parse(""));
    }

    @Test
    void parsesCommonNotations() {
        assertEquals(PdfAFlavourOption.PDF_A_2B, PdfAFlavourOption.parse("2b"));
        assertEquals(PdfAFlavourOption.PDF_A_2B, PdfAFlavourOption.parse("pdf-a-2b"));
        assertEquals(PdfAFlavourOption.PDF_A_2B, PdfAFlavourOption.parse("PDF/A-2b"));
        assertEquals(PdfAFlavourOption.PDF_A_3B, PdfAFlavourOption.parse("3b"));
        assertEquals(PdfAFlavourOption.PDF_A_1B, PdfAFlavourOption.parse("1b"));
    }

    @Test
    void rejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> PdfAFlavourOption.parse("9x"));
    }
}
