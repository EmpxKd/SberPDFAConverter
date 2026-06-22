package by.eshed.pdfa.core;

import by.eshed.pdfa.image.NormalizedPage;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.graphics.image.CCITTFactory;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Собирает PDDocument из нормализованных страниц-изображений: одна страница скана = одна
 * страница PDF, размер страницы привязан к DPI изображения. Сжатие выбирается по цветности
 * (DECISIONS.md п.8): ч/б -> CCITT Group 4, цвет/градации серого -> JPEG.
 */
public final class PdfABuilder {

    private final float jpegQuality;

    public PdfABuilder(float jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    /**
     * Собирает документ из нормализованных страниц.
     *
     * @param pages    нормализованные страницы-изображения, не пустой список
     * @param tagged   {@code true} - дополнительно построить дерево структуры Tagged PDF
     *                 (PDF/A-1a, ISO 19005-1, 6.8); {@code false} - обычная сборка (1b)
     * @param language язык документа для {@code /Lang} каталога; используется только если
     *                 {@code tagged == true}, иначе игнорируется
     */
    public BuiltDocument build(List<NormalizedPage> pages, boolean tagged, String language) throws IOException {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Нет страниц для сборки PDF");
        }
        PDDocument document = new PDDocument();
        List<PageBuildInfo> buildInfos = new ArrayList<>(pages.size());
        StructureTreeBuilder structureTreeBuilder = tagged ? new StructureTreeBuilder() : null;
        int pageIndex = 0;
        for (NormalizedPage normalizedPage : pages) {
            PDImageXObject imageObject = normalizedPage.isBilevel()
                    ? CCITTFactory.createFromImage(document, normalizedPage.image())
                    : JPEGFactory.createFromImage(document, normalizedPage.image(), jpegQuality);

            float widthPt = pixelsToPoints(normalizedPage.image().getWidth(), normalizedPage.dpi());
            float heightPt = pixelsToPoints(normalizedPage.image().getHeight(), normalizedPage.dpi());

            PDPage page = new PDPage(new PDRectangle(widthPt, heightPt));
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                if (structureTreeBuilder != null) {
                    int mcid = structureTreeBuilder.registerFigurePage(page, "Скан страницы " + (pageIndex + 1));
                    contentStream.beginMarkedContent(COSName.getPDFName(StandardStructureTypes.Figure), mcid);
                    contentStream.drawImage(imageObject, 0, 0, widthPt, heightPt);
                    contentStream.endMarkedContent();
                } else {
                    contentStream.drawImage(imageObject, 0, 0, widthPt, heightPt);
                }
            }
            buildInfos.add(new PageBuildInfo(page, normalizedPage, widthPt, heightPt));
            pageIndex++;
        }
        if (structureTreeBuilder != null) {
            structureTreeBuilder.applyTo(document, language);
        }
        return new BuiltDocument(document, buildInfos);
    }

    private static float pixelsToPoints(int pixels, float dpi) {
        return pixels / dpi * 72f;
    }
}
