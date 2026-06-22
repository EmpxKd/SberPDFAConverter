package by.eshed.pdfa.model;

import java.time.LocalDate;

/**
 * Метаданные документа для записи в docinfo и XMP (DECISIONS.md, п.4: источник — карточка СХЭД,
 * при ручном импорте — оператор). Поля опциональны: то, что не задано, не пишется ни в одну
 * из схем (а не пишется как пустая строка), чтобы не провоцировать известный баг валидации XMP
 * "title and/or subject are set" при некорректно сформированной структуре.
 */
public final class DocumentMetadata {

    private final String title;
    private final String author;
    private final String subject;
    private final String sourceSystem;
    private final String documentType;
    private final LocalDate documentDate;

    private DocumentMetadata(Builder b) {
        this.title = b.title;
        this.author = b.author;
        this.subject = b.subject;
        this.sourceSystem = b.sourceSystem;
        this.documentType = b.documentType;
        this.documentDate = b.documentDate;
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public String subject() {
        return subject;
    }

    public String sourceSystem() {
        return sourceSystem;
    }

    public String documentType() {
        return documentType;
    }

    public LocalDate documentDate() {
        return documentDate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String author;
        private String subject;
        private String sourceSystem;
        private String documentType;
        private LocalDate documentDate;

        public Builder title(String title) {
            this.title = blankToNull(title);
            return this;
        }

        public Builder author(String author) {
            this.author = blankToNull(author);
            return this;
        }

        public Builder subject(String subject) {
            this.subject = blankToNull(subject);
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = blankToNull(sourceSystem);
            return this;
        }

        public Builder documentType(String documentType) {
            this.documentType = blankToNull(documentType);
            return this;
        }

        public Builder documentDate(LocalDate documentDate) {
            this.documentDate = documentDate;
            return this;
        }

        public DocumentMetadata build() {
            return new DocumentMetadata(this);
        }

        private static String blankToNull(String value) {
            return (value == null || value.isBlank()) ? null : value.trim();
        }
    }
}
