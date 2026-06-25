package by.eshed.pdfa.model;

import java.util.Objects;

/**
 * Один входной файл скана. Для многостраничного TIFF или PDF от сканера один PageSource
 * разворачивается в несколько страниц итогового документа при нормализации.
 *
 * <p>{@code docId} группирует файлы одного документа в батч-конвертации: несколько
 * {@code PageSource} с одинаковым {@code docId} в одном батче - страницы одного итогового PDF.
 * Для одиночной конвертации ({@code POST /api/v1/convert/scan}) группировка не нужна -
 * {@link #PageSource(byte[], SourceFormat)} даёт дефолтный {@code docId="default"}.
 */
public final class PageSource {

    /** Дефолтный {@code docId} для одиночной конвертации, где группировка документов не нужна. */
    public static final String DEFAULT_DOC_ID = "default";

    private final byte[] data;
    private final SourceFormat format;
    private final String docId;
    private final String name;

    /**
     * Создаёт страницу с явным {@code docId} (батч-контекст) и именем исходного файла.
     *
     * @param data   содержимое файла, не пустое
     * @param format формат содержимого
     * @param docId  ключ документа для группировки в батче, не пустой
     * @param name   имя исходного файла для диагностики; {@code null}, если неизвестно
     */
    public PageSource(byte[] data, SourceFormat format, String docId, String name) {
        this.data = Objects.requireNonNull(data, "data");
        this.format = Objects.requireNonNull(format, "format");
        if (data.length == 0) {
            throw new IllegalArgumentException("Пустой файл скана");
        }
        this.docId = Objects.requireNonNull(docId, "docId");
        if (docId.isBlank()) {
            throw new IllegalArgumentException("docId не может быть пустым");
        }
        this.name = name;
    }

    /**
     * Создаёт страницу без явной группировки (дефолтный {@code docId}, см. {@link #DEFAULT_DOC_ID})
     * - для одиночной конвертации {@code /convert/scan}, где документ всегда один.
     */
    public PageSource(byte[] data, SourceFormat format) {
        this(data, format, DEFAULT_DOC_ID, null);
    }

    public byte[] data() {
        return data;
    }

    public SourceFormat format() {
        return format;
    }

    public String docId() {
        return docId;
    }

    /** Имя исходного файла для диагностики (логи, report.json батча); {@code null}, если неизвестно. */
    public String name() {
        return name;
    }
}
