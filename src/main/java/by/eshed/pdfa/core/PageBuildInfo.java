package by.eshed.pdfa.core;

import by.eshed.pdfa.image.NormalizedPage;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Связывает собранную страницу PDF с исходным растром и DPI — нужно последующему шагу OCR,
 * чтобы пересчитать пиксельные координаты слов в координаты страницы PDF.
 */
public final class PageBuildInfo {

    private final PDPage pdPage;
    private final NormalizedPage source;
    private final float widthPt;
    private final float heightPt;

    public PageBuildInfo(PDPage pdPage, NormalizedPage source, float widthPt, float heightPt) {
        this.pdPage = pdPage;
        this.source = source;
        this.widthPt = widthPt;
        this.heightPt = heightPt;
    }

    public PDPage pdPage() {
        return pdPage;
    }

    public NormalizedPage source() {
        return source;
    }

    public float widthPt() {
        return widthPt;
    }

    public float heightPt() {
        return heightPt;
    }
}
