package by.eshed.pdfa.http;

import by.eshed.pdfa.model.SourceFormat;

/**
 * Часть multipart-клиентов не присылает корректный Content-Type для файловой части (браузеры
 * иногда шлют application/octet-stream) — в таком случае определяем формат по расширению имени файла.
 */
public final class SourceFormats {

    private SourceFormats() {
    }

    public static SourceFormat detect(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equalsIgnoreCase(contentType.trim())) {
            try {
                return SourceFormat.fromContentType(contentType);
            } catch (IllegalArgumentException ignored) {
                // падать рано не нужно - пробуем определить по имени файла ниже
            }
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
                return SourceFormat.TIFF;
            }
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return SourceFormat.JPEG;
            }
            if (lower.endsWith(".png")) {
                return SourceFormat.PNG;
            }
            if (lower.endsWith(".bmp")) {
                return SourceFormat.BMP;
            }
            if (lower.endsWith(".pdf")) {
                return SourceFormat.PDF;
            }
        }
        throw new IllegalArgumentException("Не удалось определить формат файла: " + filename
                + " (Content-Type: " + contentType + ")");
    }
}
