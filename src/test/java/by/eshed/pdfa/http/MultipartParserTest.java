package by.eshed.pdfa.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartParserTest {

    @Test
    void parsesTextAndFileParts() {
        String boundary = "TestBoundary123";
        byte[] fileBytes = {0x42, 0x00, (byte) 0xFF, 0x10};
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
                + "Дело №1\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"page\"; filename=\"scan.tif\"\r\n"
                + "Content-Type: image/tiff\r\n\r\n";

        byte[] bodyBytes = concat(
                body.getBytes(StandardCharsets.UTF_8),
                fileBytes,
                ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        List<MultipartParser.Part> parts = MultipartParser.parse(bodyBytes, boundary);

        assertEquals(2, parts.size());

        MultipartParser.Part titlePart = parts.get(0);
        assertEquals("title", titlePart.name());
        assertFalse(titlePart.isFile());
        assertEquals("Дело №1", new String(titlePart.data(), StandardCharsets.UTF_8));

        MultipartParser.Part filePart = parts.get(1);
        assertEquals("page", filePart.name());
        assertEquals("scan.tif", filePart.filename());
        assertEquals("image/tiff", filePart.contentType());
        assertTrue(filePart.isFile());
        assertArrayEquals(fileBytes, filePart.data());
    }

    @Test
    void returnsEmptyListWhenNoParts() {
        String boundary = "B";
        byte[] body = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        assertTrue(MultipartParser.parse(body, boundary).isEmpty());
    }

    private static byte[] concat(byte[]... chunks) {
        int total = 0;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return result;
    }
}
