package by.eshed.pdfa.http;

import by.eshed.pdfa.model.SourceFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Часть клиентов шлёт application/octet-stream вместо реального Content-Type - нужен fallback по имени файла. */
class SourceFormatsTest {

    @Test
    void usesContentTypeWhenMeaningful() {
        assertEquals(SourceFormat.PNG, SourceFormats.detect("image/png", "scan.bin"));
    }

    @Test
    void fallsBackToFilenameExtensionWhenContentTypeIsOctetStream() {
        assertEquals(SourceFormat.TIFF, SourceFormats.detect("application/octet-stream", "scan.tif"));
        assertEquals(SourceFormat.JPEG, SourceFormats.detect("application/octet-stream", "scan.jpeg"));
        assertEquals(SourceFormat.PDF, SourceFormats.detect(null, "scan.pdf"));
    }

    @Test
    void rejectsUnsupportedFormatWhenNeitherContentTypeNorFilenameMatch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SourceFormats.detect("application/octet-stream", "scan.docx"));
        assertEquals("Не удалось определить формат файла: scan.docx (Content-Type: application/octet-stream)",
                ex.getMessage());
    }
}