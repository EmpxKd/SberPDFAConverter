package by.eshed.pdfa.ocr;

import by.eshed.pdfa.core.PageBuildInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Кладёт слова OCR невидимым текстом (Tr 3 / NEITHER) поверх изображения страницы — документ
 * становится поисковым и копируемым без необходимости тегированной структуры (PDF/A-...b
 * достаточно, см. DECISIONS.md п.2). Координаты слов из OCR заданы в пикселях исходного
 * изображения и пересчитываются в точки PDF через DPI нормализованной страницы.
 */
public final class InvisibleTextLayerWriter {

    private static final Logger LOG = Logger.getLogger(InvisibleTextLayerWriter.class.getName());

    private InvisibleTextLayerWriter() {
    }

    public static void write(PDDocument document, PageBuildInfo pageInfo, List<RecognizedWord> words, PDFont font)
            throws IOException {
        if (words.isEmpty()) {
            return;
        }
        float dpi = pageInfo.source().dpi();
        float heightPt = pageInfo.heightPt();

        try (PDPageContentStream contentStream = new PDPageContentStream(document, pageInfo.pdPage(),
                PDPageContentStream.AppendMode.APPEND, true)) {
            contentStream.setRenderingMode(RenderingMode.NEITHER);
            for (RecognizedWord word : words) {
                // Проверяем кодируемость ДО beginText: если прервать BT/ET исключением
                // посередине, content stream останется повреждён (непарный BT).
                try {
                    font.encode(word.text());
                } catch (IOException | IllegalArgumentException unmappableGlyph) {
                    LOG.log(Level.FINE, "Пропущено слово OCR с неотображаемым символом: {0}", word.text());
                    continue;
                }

                float xPt = pixelsToPoints(word.x(), dpi);
                float wordHeightPt = pixelsToPoints(word.height(), dpi);
                float yTopPt = pixelsToPoints(word.y(), dpi);
                float yBaselinePt = heightPt - yTopPt - wordHeightPt;
                float fontSize = Math.max(wordHeightPt, 1f);

                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(xPt, yBaselinePt);
                contentStream.showText(word.text());
                contentStream.endText();
            }
        }
    }

    private static float pixelsToPoints(int pixels, float dpi) {
        return pixels / dpi * 72f;
    }
}
