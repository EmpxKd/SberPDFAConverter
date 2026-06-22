package by.eshed.pdfa.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF/A требует встроенный device-independent ICC OutputIntent (описание задачи 1.txt:
 * "цвет device-independent"). Профиль sRGB IEC61966-2.1 общий для всего проекта (DECISIONS.md п.1).
 */
public final class OutputIntentFactory {

    private static final String ICC_RESOURCE = "/icc/sRGB.icc";

    private OutputIntentFactory() {
    }

    public static void addSRgbOutputIntent(PDDocument document) throws IOException {
        try (InputStream iccStream = OutputIntentFactory.class.getResourceAsStream(ICC_RESOURCE)) {
            if (iccStream == null) {
                throw new IOException("ICC-профиль не найден в ресурсах: " + ICC_RESOURCE);
            }
            PDOutputIntent outputIntent = new PDOutputIntent(document, iccStream);
            outputIntent.setInfo("sRGB IEC61966-2.1");
            outputIntent.setOutputCondition("sRGB IEC61966-2.1");
            outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            outputIntent.setRegistryName("http://www.color.org");
            document.getDocumentCatalog().addOutputIntent(outputIntent);
        }
    }
}
