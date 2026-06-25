package by.eshed.pdfa.model;

import java.util.List;
import java.util.Objects;

public final class ConversionRequest {

    private final List<PageSource> pages;
    private final DocumentMetadata metadata;
    private final PdfAFlavourOption flavour;
    private final boolean strictValidation;

    private ConversionRequest(Builder b) {
        this.pages = List.copyOf(b.pages);
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("Нужна хотя бы одна страница скана");
        }
        this.metadata = b.metadata != null ? b.metadata : DocumentMetadata.builder().build();
        this.flavour = b.flavour;
        this.strictValidation = b.strictValidation;
    }

    public List<PageSource> pages() {
        return pages;
    }

    public DocumentMetadata metadata() {
        return metadata;
    }

    public PdfAFlavourOption flavour() {
        return flavour;
    }


    public boolean strictValidation() {
        return strictValidation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<PageSource> pages = List.of();
        private DocumentMetadata metadata;
        private PdfAFlavourOption flavour = PdfAFlavourOption.PDF_A_1B; // дефолт PDF/A-1b
        private boolean strictValidation = true; // veraPDF как обязательный гейт

        public Builder pages(List<PageSource> pages) {
            this.pages = Objects.requireNonNull(pages, "pages");
            return this;
        }

        public Builder metadata(DocumentMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder flavour(PdfAFlavourOption flavour) {
            this.flavour = Objects.requireNonNull(flavour, "flavour");
            return this;
        }

        public Builder strictValidation(boolean strictValidation) {
            this.strictValidation = strictValidation;
            return this;
        }

        public ConversionRequest build() {
            return new ConversionRequest(this);
        }
    }
}
