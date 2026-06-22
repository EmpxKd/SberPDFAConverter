package by.eshed.pdfa.model;

import java.util.Objects;

/**
 * Один входной файл скана. Для многостраничного TIFF или PDF от сканера один PageSource
 * разворачивается в несколько страниц итогового документа при нормализации.
 */
public final class PageSource {

    private final byte[] data;
    private final SourceFormat format;

    public PageSource(byte[] data, SourceFormat format) {
        this.data = Objects.requireNonNull(data, "data");
        this.format = Objects.requireNonNull(format, "format");
        if (data.length == 0) {
            throw new IllegalArgumentException("Пустой файл скана");
        }
    }

    public byte[] data() {
        return data;
    }

    public SourceFormat format() {
        return format;
    }
}
