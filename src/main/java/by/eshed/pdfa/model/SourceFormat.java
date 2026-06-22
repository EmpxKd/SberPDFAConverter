package by.eshed.pdfa.model;

/**
 * Допустимые форматы входа скана (DECISIONS.md, п.3: "универсальный приём").
 */
public enum SourceFormat {
    TIFF,
    JPEG,
    PNG,
    BMP,
    PDF;

    public static SourceFormat fromContentType(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Content-Type не указан");
        }
        String normalized = contentType.toLowerCase().trim();
        if (normalized.contains("tiff") || normalized.contains("tif")) {
            return TIFF;
        }
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return JPEG;
        }
        if (normalized.contains("png")) {
            return PNG;
        }
        if (normalized.contains("bmp")) {
            return BMP;
        }
        if (normalized.contains("pdf")) {
            return PDF;
        }
        throw new IllegalArgumentException("Неподдерживаемый формат входа: " + contentType);
    }
}
