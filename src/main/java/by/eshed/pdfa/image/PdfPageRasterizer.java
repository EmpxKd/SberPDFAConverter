package by.eshed.pdfa.image;

import by.eshed.pdfa.model.PageSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Растеризует постраничный PDF, собранный сканером (входной формат "уже PDF от сканера",
 * описание задачи 1.txt п.3), в изображения по тому же конвейеру, что и обычные сканы — на
 * выходе всегда заново собранный PDFBox-ом PDF/A, а не исходный файл сканера as-is.
 */
public final class PdfPageRasterizer {

    private final float targetDpi;

    public PdfPageRasterizer(float targetDpi) {
        this.targetDpi = targetDpi;
    }

    public List<NormalizedPage> readPages(PageSource source) throws IOException {
        if (source.format() != by.eshed.pdfa.model.SourceFormat.PDF) {
            throw new IllegalArgumentException("PdfPageRasterizer принимает только PDF");
        }
        List<NormalizedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(source.data())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, targetDpi, ImageType.RGB);
                // Исходная цветность отдельной страницы сканер-PDF не анализируется -
                // рендерится как цветное изображение (упрощение, см. IMPLEMENTATION_LOG.md).
                pages.add(new NormalizedPage(image, targetDpi, false));
            }
        }
        return pages;
    }
}
