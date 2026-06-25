package by.eshed.pdfa.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Метаданные документа для записи в docinfo и XMP. Поля опциональны: то, что не задано, не
 * пишется ни в одну из схем (а не пишется как пустая строка).
 *
 * Помимо фиксированных полей, заказчик может передать произвольные пары "ключ-значение"
 * ({@link #customProperties()}). Они пишутся отдельно от стандартных полей через
 * {@link by.eshed.pdfa.metadata.MetadataMapper} в PDF/A extension schema, поэтому зарезервированные
 * имена стандартных полей Info/XMP здесь отклоняются — иначе они могли бы конфликтовать со
 * стандартными значениями (title/author/...) при чтении.
 */
public final class DocumentMetadata {

    /**
     * Имена стандартных полей docinfo/XMP (case-insensitive) — нельзя использовать как ключ
     * произвольного свойства, чтобы не конфликтовать со стандартными значениями документа.
     */
    private static final Set<String> RESERVED_CUSTOM_KEYS = Set.of(
            "title", "author", "subject", "keywords", "creator", "producer", "creationdate", "moddate");

    private final String title;
    private final String author;
    private final String subject;
    private final String sourceSystem;
    private final String documentType;
    private final LocalDate documentDate;
    private final String language;
    private final Map<String, String> customProperties;

    private DocumentMetadata(Builder b) {
        this.title = b.title;
        this.author = b.author;
        this.subject = b.subject;
        this.sourceSystem = b.sourceSystem;
        this.documentType = b.documentType;
        this.documentDate = b.documentDate;
        this.language = b.language;
        // Map.copyOf не гарантирует порядок вставки - заказчик может ожидать его сохранение
        // при выводе, поэтому оборачиваем LinkedHashMap, а не используем Map.copyOf.
        this.customProperties = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(b.customProperties));
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

    /**
     * Язык документа (BCP-47/ISO 639, например "ru") для каталожного {@code /Lang} —
     * нужен только для тегированного PDF/A-1a. Не задан → {@code null}.
     */
    public String language() {
        return language;
    }

    /**
     * Произвольные метаданные "ключ-значение", заданные заказчиком. Неизменяемая копия, порядок
     * вставки сохранён; пустая {@code Map}, если ничего не задано (никогда {@code null}).
     */
    public Map<String, String> customProperties() {
        return customProperties;
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
        private String language;
        private final Map<String, String> customProperties = new LinkedHashMap<>();

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

        public Builder language(String language) {
            this.language = blankToNull(language);
            return this;
        }

        /**
         * Добавляет одно произвольное свойство "ключ-значение". Пустой/{@code null}
         * ключ или значение — пропускается без ошибки (не строгий контракт формы). Повторный
         * вызов с уже использованным ключом перезаписывает значение, сохраняя исходную позицию
         * ключа в порядке вставки.
         *
         * @param key   имя свойства; обрезается по пробелам
         * @param value значение свойства
         * @return this
         * @throws IllegalArgumentException если {@code key} (без учёта регистра) совпадает с именем
         *                                   стандартного поля Info/XMP (title/author/subject/keywords/
         *                                   creator/producer/creationdate/moddate)
         */
        public Builder customProperty(String key, String value) {
            String trimmedKey = blankToNull(key);
            String trimmedValue = blankToNull(value);
            if (trimmedKey == null || trimmedValue == null) {
                return this;
            }
            if (RESERVED_CUSTOM_KEYS.contains(trimmedKey.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Имя произвольного свойства совпадает со стандартным полем метаданных: " + trimmedKey);
            }
            this.customProperties.put(trimmedKey, trimmedValue);
            return this;
        }

        /**
         * Добавляет все пары "ключ-значение" из {@code properties} через {@link #customProperty},
         * сохраняя порядок итерации входной карты.
         *
         * @param properties карта произвольных свойств; {@code null} равносилен пустой карте
         * @return this
         * @throws IllegalArgumentException если какой-либо ключ зарезервирован (см. {@link #customProperty})
         */
        public Builder customProperties(Map<String, String> properties) {
            if (properties == null) {
                return this;
            }
            properties.forEach(this::customProperty);
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
