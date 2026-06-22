package by.eshed.pdfa.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceFormatTest {

    @Test
    void detectsKnownContentTypes() {
        assertEquals(SourceFormat.TIFF, SourceFormat.fromContentType("image/tiff"));
        assertEquals(SourceFormat.JPEG, SourceFormat.fromContentType("image/jpeg"));
        assertEquals(SourceFormat.PNG, SourceFormat.fromContentType("image/png"));
        assertEquals(SourceFormat.BMP, SourceFormat.fromContentType("image/bmp"));
        assertEquals(SourceFormat.PDF, SourceFormat.fromContentType("application/pdf"));
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThrows(IllegalArgumentException.class, () -> SourceFormat.fromContentType("application/x-unknown"));
    }

    @Test
    void rejectsNullContentType() {
        assertThrows(IllegalArgumentException.class, () -> SourceFormat.fromContentType(null));
    }
}