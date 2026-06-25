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
import java.util.List;

/**
 * Собирает PDDocument из нормализованных страниц-изображений: одна страница скана = одна
 * страница PDF, размер страницы привязан к DPI изображения. Сжатие выбирается по цветности:
 * ч/б -> CCITT Group 4, цвет/градации серого -> JPEG.
 */
public final class PdfABuilder {

    private final float jpegQuality;

    public PdfABuilder(float jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    /**
     * Собирает документ из нормализованных страниц (нужны все страницы заранее - используется
     * для тегированного 1a, где {@code /StructTreeRoot} требует знать страницы до завершения
     * дерева структуры). Для потоковой сборки 1b см. {@link #addImagePage}.
     *
     * @param pages    нормализованные страницы-изображения, не пустой список
     * @param tagged   {@code true} - дополнительно построить дерево структуры Tagged PDF
     *                 (PDF/A-1a, ISO 19005-1, 6.8); {@code false} - обычная сборка (1b)
     * @param language язык документа для {@code /Lang} каталога; используется только если
     *                 {@code tagged == true}, иначе игнорируется
     */
    public PDDocument build(List<NormalizedPage> pages, boolean tagged, String language) throws IOException {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Нет страниц для сборки PDF");
        }
        PDDocument document = new PDDocument();
        StructureTreeBuilder structureTreeBuilder = tagged ? new StructureTreeBuilder() : null;
        int pageIndex = 0;
        for (NormalizedPage normalizedPage : pages) {
            addImagePage(document, normalizedPage, structureTreeBuilder, pageIndex++);
        }
        if (structureTreeBuilder != null) {
            structureTreeBuilder.applyTo(document, language);
        }
        return document;
    }

    /**
     * Дорисовывает одну нормализованную страницу в уже открытый документ: выбирает сжатие
     * (CCITT Group 4 для ч/б, JPEG для цвета/градаций серого), считает размер страницы из DPI,
     * рисует изображение. Используется как потоковой сборкой 1b (страница за страницей,
     * без накопления всех страниц документа в памяти), так и {@link #build} для 1a.
     *
     * @param document             документ, в который добавляется страница
     * @param page                 нормализованная страница-изображение
     * @param structureTreeBuilder построитель дерева структуры Tagged PDF; {@code null} - не
     *                             оборачивать изображение в marked-content (PDF/A-1b)
     * @param pageIndex            0-based номер страницы в документе (для alt-текста структуры)
     */
    public void addImagePage(PDDocument document, NormalizedPage page,
                              StructureTreeBuilder structureTreeBuilder, int pageIndex) throws IOException {
        PDImageXObject imageObject = page.isBilevel()
                ? CCITTFactory.createFromImage(document, page.image())
                : JPEGFactory.createFromImage(document, page.image(), jpegQuality);

        float widthPt = pixelsToPoints(page.image().getWidth(), page.dpi());
        float heightPt = pixelsToPoints(page.image().getHeight(), page.dpi());

        PDPage pdPage = new PDPage(new PDRectangle(widthPt, heightPt));
        document.addPage(pdPage);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, pdPage)) {
            if (structureTreeBuilder != null) {
                int mcid = structureTreeBuilder.registerFigurePage(pdPage, "Скан страницы " + (pageIndex + 1));
                contentStream.beginMarkedContent(COSName.getPDFName(StandardStructureTypes.Figure), mcid);
                contentStream.drawImage(imageObject, 0, 0, widthPt, heightPt);
                contentStream.endMarkedContent();
            } else {
                contentStream.drawImage(imageObject, 0, 0, widthPt, heightPt);
            }
        }
    }

    private static float pixelsToPoints(int pixels, float dpi) {
        return pixels / dpi * 72f;
    }
}
