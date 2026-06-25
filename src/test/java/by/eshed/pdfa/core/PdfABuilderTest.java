package by.eshed.pdfa.core;

import by.eshed.pdfa.image.NormalizedPage;
import by.eshed.pdfa.testutil.TestImages;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, задача 5: тегированная структура (/MarkInfo, /StructTreeRoot, /Lang) должна строиться
 * только при {@code tagged == true} (PDF/A-1a) и не появляться для 1b.
 */
class PdfABuilderTest {

    private final PdfABuilder builder = new PdfABuilder(0.75f);

    @Test
    void taggedBuildWritesStructureTreeAndMarkInfoAndLang() throws Exception {
        NormalizedPage page = new NormalizedPage(TestImages.colorPage(100, 80), 300f, false);

        try (var document = builder.build(List.of(page), true, "ru")) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            assertNotNull(catalog.getStructureTreeRoot(), "/StructTreeRoot должен быть записан для 1a");
            assertNotNull(catalog.getMarkInfo(), "/MarkInfo должен быть записан для 1a");
            assertTrue(catalog.getMarkInfo().isMarked());
            assertEquals("ru", catalog.getLanguage());
        }
    }

    @Test
    void untaggedBuildHasNoStructureTree() throws Exception {
        NormalizedPage page = new NormalizedPage(TestImages.colorPage(100, 80), 300f, false);

        try (var document = builder.build(List.of(page), false, "ru")) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            assertNull(catalog.getStructureTreeRoot(), "1b не должен получать структуру тегов");
            assertFalse(catalog.getMarkInfo() != null && catalog.getMarkInfo().isMarked());
        }
    }
}