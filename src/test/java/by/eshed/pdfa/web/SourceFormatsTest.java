package by.eshed.pdfa.web;

import by.eshed.pdfa.model.SourceFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFormatsTest {

    @Test
    void detectsFormatByContentType() {
        assertEquals(SourceFormat.TIFF, SourceFormats.detect("image/tiff", "scan.bin"));
        assertEquals(SourceFormat.JPEG, SourceFormats.detect("image/jpeg", "scan.bin"));
        assertEquals(SourceFormat.PNG, SourceFormats.detect("image/png", "scan.bin"));
        assertEquals(SourceFormat.BMP, SourceFormats.detect("image/bmp", "scan.bin"));
        assertEquals(SourceFormat.PDF, SourceFormats.detect("application/pdf", "scan.bin"));
    }

    @Test
    void fallsBackToFilenameWhenContentTypeMissingOrGeneric() {
        assertEquals(SourceFormat.TIFF, SourceFormats.detect(null, "scan.tiff"));
        assertEquals(SourceFormat.TIFF, SourceFormats.detect("", "scan.tif"));
        assertEquals(SourceFormat.JPEG, SourceFormats.detect("application/octet-stream", "scan.JPG"));
        assertEquals(SourceFormat.PNG, SourceFormats.detect("application/octet-stream", "scan.png"));
        assertEquals(SourceFormat.BMP, SourceFormats.detect(null, "scan.bmp"));
        assertEquals(SourceFormat.PDF, SourceFormats.detect(null, "scan.pdf"));
    }

    @Test
    void fallsBackToFilenameWhenContentTypeUnrecognized() {
        assertEquals(SourceFormat.PNG, SourceFormats.detect("text/plain", "scan.png"));
    }

    @Test
    void throwsWhenNeitherContentTypeNorFilenameAreRecognizable() {
        assertThrows(IllegalArgumentException.class, () -> SourceFormats.detect(null, null));
        assertThrows(IllegalArgumentException.class, () -> SourceFormats.detect("text/plain", "scan.unknown"));
    }
}
