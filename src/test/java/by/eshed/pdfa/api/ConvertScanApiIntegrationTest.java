package by.eshed.pdfa.api;

import by.eshed.pdfa.http.PdfAHttpServer;
import by.eshed.pdfa.ocr.TesseractOcrEngine;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import by.eshed.pdfa.testutil.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, "Задачи для тестировщика", сценарий 6: реальный HTTP-сервер ({@link PdfAHttpServer}),
 * реальный multipart-запрос - проверяем границу REST API, а не только внутренние классы.
 */
class ConvertScanApiIntegrationTest {

    private PdfAHttpServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        TesseractOcrEngine ocrEngine = new TesseractOcrEngine(System.getenv("TESSDATA_PREFIX"));
        ScanToPdfAConverter converter = new ScanToPdfAConverter(300f, 0.75f, ocrEngine);
        server = new PdfAHttpServer(0, converter, ocrEngine);
        server.start();
        baseUrl = "http://localhost:" + server.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void rejectsSignaturePartWith400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] signature = "dummy-signature".getBytes(StandardCharsets.UTF_8);
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .filePart("signature", "sig.bin", "application/octet-stream", signature)
                .build();

        HttpResponse<String> response = post("/api/v1/convert/scan", boundary, body);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("подпис"), "ответ должен пояснять причину отказа: " + response.body());
    }

    @Test
    void rejectsNonPart1FlavourWith400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .textPart("flavour", "2b")
                .build();

        HttpResponse<String> response = post("/api/v1/convert/scan", boundary, body);

        assertEquals(400, response.statusCode());
    }

    @Test
    void convertsNormalScanWith200AndPdfContentType() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .textPart("ocrEnabled", "false")
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(null));
        assertTrue(response.body().length > 0, "тело ответа должно содержать PDF");
        assertEquals("%PDF", new String(response.body(), 0, 4, StandardCharsets.US_ASCII));
    }

    private HttpResponse<String> post(String path, String boundary, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static MultipartBuilder multipart(String boundary) {
        return new MultipartBuilder(boundary);
    }

    /** Собирает multipart/form-data тело запроса вручную - в проекте нет HTTP-клиента с такой поддержкой. */
    private static final class MultipartBuilder {
        private final String boundary;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        MultipartBuilder(String boundary) {
            this.boundary = boundary;
        }

        MultipartBuilder textPart(String name, String value) {
            write("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n");
            return this;
        }

        MultipartBuilder filePart(String name, String filename, String contentType, byte[] data) {
            write("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n");
            out.writeBytes(data);
            write("\r\n");
            return this;
        }

        byte[] build() {
            write("--" + boundary + "--\r\n");
            return out.toByteArray();
        }

        private void write(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
