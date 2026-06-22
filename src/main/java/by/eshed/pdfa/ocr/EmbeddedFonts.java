package by.eshed.pdfa.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Шрифт для невидимого текстового слоя OCR должен поддерживать кириллицу — встроенные 14
 * стандартных PDF-шрифтов её не покрывают. DejaVu Sans (Bitstream Vera license, свободно для
 * коммерческого использования) встраивается с подмножеством символов (embedSubset=true).
 */
public final class EmbeddedFonts {

    private static final String FONT_RESOURCE = "/fonts/DejaVuSans.ttf";

    private EmbeddedFonts() {
    }

    public static PDFont loadOcrFont(PDDocument document) throws IOException {
        try (InputStream fontStream = EmbeddedFonts.class.getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IOException("Шрифт не найден в ресурсах: " + FONT_RESOURCE);
            }
            return PDType0Font.load(document, fontStream, true);
        }
    }
}
