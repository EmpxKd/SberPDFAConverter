package by.eshed.pdfa.core;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.util.List;

public final class BuiltDocument {

    private final PDDocument document;
    private final List<PageBuildInfo> pages;

    public BuiltDocument(PDDocument document, List<PageBuildInfo> pages) {
        this.document = document;
        this.pages = List.copyOf(pages);
    }

    public PDDocument document() {
        return document;
    }

    public List<PageBuildInfo> pages() {
        return pages;
    }
}
