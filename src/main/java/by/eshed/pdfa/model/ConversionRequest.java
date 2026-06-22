package by.eshed.pdfa.model;

import java.util.List;
import java.util.Objects;

public final class ConversionRequest {

    private final List<PageSource> pages;
    private final DocumentMetadata metadata;
    private final boolean ocrEnabled;
    private final String ocrLanguage;
    private final PdfAFlavourOption flavour;
    private final SignatureAttachment attachment;
    private final boolean strictValidation;

    private ConversionRequest(Builder b) {
        this.pages = List.copyOf(b.pages);
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("Нужна хотя бы одна страница скана");
        }
        this.metadata = b.metadata != null ? b.metadata : DocumentMetadata.builder().build();
        this.ocrEnabled = b.ocrEnabled;
        this.ocrLanguage = b.ocrLanguage;
        // Вложение файла подписи возможно только в PDF/A-3 (A-3 разрешает embedded files,
        // см. DECISIONS.md п.5) — при наличии вложения профиль повышается автоматически.
        this.flavour = b.attachment != null ? PdfAFlavourOption.PDF_A_3B : b.flavour;
        this.attachment = b.attachment;
        this.strictValidation = b.strictValidation;
    }

    public List<PageSource> pages() {
        return pages;
    }

    public DocumentMetadata metadata() {
        return metadata;
    }

    public boolean ocrEnabled() {
        return ocrEnabled;
    }

    public String ocrLanguage() {
        return ocrLanguage;
    }

    public PdfAFlavourOption flavour() {
        return flavour;
    }

    public SignatureAttachment attachment() {
        return attachment;
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
        private boolean ocrEnabled = true; // DECISIONS.md п.2: OCR всегда
        private String ocrLanguage = "rus+eng";
        private PdfAFlavourOption flavour = PdfAFlavourOption.PDF_A_2B; // DECISIONS.md п.1
        private SignatureAttachment attachment;
        private boolean strictValidation = true; // veraPDF как обязательный гейт

        public Builder pages(List<PageSource> pages) {
            this.pages = Objects.requireNonNull(pages, "pages");
            return this;
        }

        public Builder metadata(DocumentMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder ocrEnabled(boolean ocrEnabled) {
            this.ocrEnabled = ocrEnabled;
            return this;
        }

        public Builder ocrLanguage(String ocrLanguage) {
            if (ocrLanguage != null && !ocrLanguage.isBlank()) {
                this.ocrLanguage = ocrLanguage;
            }
            return this;
        }

        public Builder flavour(PdfAFlavourOption flavour) {
            this.flavour = Objects.requireNonNull(flavour, "flavour");
            return this;
        }

        public Builder attachment(SignatureAttachment attachment) {
            this.attachment = attachment;
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
