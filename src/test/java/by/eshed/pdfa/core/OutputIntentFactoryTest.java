package by.eshed.pdfa.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PDFA1_REQUIREMENTS.md: PDF/A требует ровно один встроенный device-independent ICC OutputIntent. */
class OutputIntentFactoryTest {

    @Test
    void addsExactlyOneSRgbOutputIntent() throws Exception {
        try (PDDocument document = new PDDocument()) {
            OutputIntentFactory.addSRgbOutputIntent(document);

            List<PDOutputIntent> intents = document.getDocumentCatalog().getOutputIntents();
            assertEquals(1, intents.size());
            PDOutputIntent intent = intents.get(0);
            assertEquals("sRGB IEC61966-2.1", intent.getInfo());
            assertEquals("sRGB IEC61966-2.1", intent.getOutputCondition());
            assertEquals("sRGB IEC61966-2.1", intent.getOutputConditionIdentifier());
        }
    }
}