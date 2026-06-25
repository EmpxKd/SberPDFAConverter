package by.eshed.pdfa.api;

import by.eshed.pdfa.testutil.TestImages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, "Задачи для тестировщика", сценарий 5: реальная HTTP-граница (embedded Tomcat, не
 * MockMvc) + НЕ замоканный конвейер, включая настоящий обязательный гейт veraPDF — именно это
 * нужно для главного риска миграции на Spring Boot (Greenfield-движок под nested-classloader).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConvertScanApiIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

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
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(null));
        assertTrue(response.body().length > 0, "тело ответа должно содержать PDF");
        assertEquals("%PDF", new String(response.body(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void convertsScanWithCustomMetaFields() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .textPart("meta", "НомерДела=12345")
                .textPart("meta", "Отдел=Бухгалтерия")
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(null));
    }

    /**
     * PLAN.md, "Задачи для тестировщика", сценарий 6: многостраничный TIFF одной файловой частью
     * через настоящую multipart-границу (не вызов конвейера напрямую, как в
     * {@code pipeline.ScanToPdfAConverterTest}) - страницы должны и пройти Spring multipart-приём,
     * и сохраниться в PDF-результате в исходном количестве.
     */
    @Test
    void convertsMultiPageTiffOverHttpWithAllPagesPreserved() throws Exception {
        byte[] tiff = TestImages.encodeMultiPageTiff(
                List.of(TestImages.bilevelPage(150, 100), TestImages.bilevelPage(150, 100),
                        TestImages.bilevelPage(150, 100)));
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "scan.tiff", "image/tiff", tiff)
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        try (PDDocument document = Loader.loadPDF(response.body())) {
            assertEquals(3, document.getNumberOfPages());
        }
    }

    /**
     * PLAN.md, сценарий 6: несколько файловых частей с одинаковым именем {@code page} (как при
     * постраничной загрузке скана со сканера, см. RUNBOOK.md §3) - порядок страниц в результате
     * должен совпасть с порядком частей запроса.
     */
    @Test
    void convertsMultipleSeparatePagePartsInOrder() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "page1.png", "image/png", TestImages.encode(TestImages.bilevelPage(50, 40), "png"))
                .filePart("page", "page2.png", "image/png", TestImages.encode(TestImages.colorPage(50, 40), "png"))
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        try (PDDocument document = Loader.loadPDF(response.body())) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    /**
     * PLAN.md, сценарий 6 ("проверка лимитов multipart"): файл заметно крупнее дефолтного лимита
     * Spring Boot ({@code spring.servlet.multipart.max-file-size=1MB}) должен пройти благодаря
     * увеличенному лимиту в {@code application.yml} (100MB/300MB) - если конфигурация лимитов
     * будет случайно отменена/уменьшена, этот тест упадёт с 400/413 раньше, чем заметит реальный
     * пользователь с крупным сканом.
     */
    @Test
    void convertsFileLargerThanDefaultSpringMultipartLimit() throws Exception {
        byte[] bmp = TestImages.encodeUncompressedBmp(TestImages.colorPage(1600, 1200));
        assertTrue(bmp.length > 1024 * 1024, "файл должен быть больше дефолтного лимита Spring (1MB): " + bmp.length);

        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "large.bmp", "image/bmp", bmp)
                .build();

        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    void rejectsMalformedMetaFieldWith400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .textPart("meta", "НомерДела_без_равно")
                .build();

        HttpResponse<String> response = post("/api/v1/convert/scan", boundary, body);

        assertEquals(400, response.statusCode());
    }

    @Test
    void rejectsReservedKeyInMetaFieldWith400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] body = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .textPart("meta", "title=Подмена")
                .build();

        HttpResponse<String> response = post("/api/v1/convert/scan", boundary, body);

        assertEquals(400, response.statusCode());
    }

    @Test
    void validatesPostedPdfAndReturnsJsonReport() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] page = TestImages.encode(TestImages.bilevelPage(50, 40), "png");
        byte[] convertBody = multipart(boundary)
                .filePart("page", "scan.png", "image/png", page)
                .build();
        HttpResponse<byte[]> converted = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/scan"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(convertBody))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, converted.statusCode());

        HttpResponse<String> validated = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/validate?flavour=1b"))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(converted.body()))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, validated.statusCode());
        assertTrue(validated.body().contains("\"compliant\":true"), validated.body());
    }

    @Test
    void healthzReturns200() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/healthz"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
        assertTrue(!response.body().contains("tesseractAvailable"),
                "поле tesseractAvailable удалено вместе с OCR: " + response.body());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, String boundary, byte[] body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + path))
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
