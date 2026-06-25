package by.eshed.pdfa.batch;

import by.eshed.pdfa.testutil.TestImages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, "Задачи для тестировщика", сценарии 4/5/6 — HTTP-контракт {@code /api/v1/convert/batch}
 * через настоящий embedded Tomcat (как {@code ConvertScanApiIntegrationTest} для {@code /convert/scan}).
 * Внутренняя логика батча (изоляция сбоев, порядок страниц, параллелизм, TTL) — отдельно в
 * {@link BatchJobServiceTest}, без HTTP-слоя.
 *
 * <p>{@code queue-capacity=5} специально маленькая - нужна для детерминированного сценария 4
 * (переполнение очереди), без долгого ожидания реального переполнения под нагрузкой.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"pdfa.batch.queue-capacity=5", "pdfa.batch.workers=2"})
class BatchConvertApiIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void submitBatchGroupsByDocIdAndZipContainsAllCompliantPdfs() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "a1.png", "image/png", png(50, 40))
                .textPart("docId", "docA")
                .filePart("page", "a2.png", "image/png", png(50, 40))
                .textPart("docId", "docA")
                .filePart("page", "b1.png", "image/png", png(60, 30))
                .textPart("docId", "docB")
                .build();

        HttpResponse<String> submitted = post("/api/v1/convert/batch", boundary, body);
        assertEquals(202, submitted.statusCode(), submitted.body());
        String jobId = extractJsonString(submitted.body(), "jobId");
        assertTrue(submitted.body().contains("\"documents\":2"), submitted.body());

        String statusJson = awaitJobDone(jobId);
        assertTrue(statusJson.contains("\"total\":2"), statusJson);
        assertTrue(statusJson.contains("\"done\":2"), statusJson);
        assertTrue(statusJson.contains("\"failed\":0"), statusJson);

        HttpResponse<byte[]> zipResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/batch/" + jobId + "/result")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, zipResponse.statusCode());
        assertEquals("application/zip", zipResponse.headers().firstValue("Content-Type").orElse(null));

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipResponse.body()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("docA.pdf")) {
                    assertEquals(2, loadPageCount(zip.readAllBytes()), "docA должен содержать 2 страницы");
                } else if (entry.getName().equals("docB.pdf")) {
                    assertEquals(1, loadPageCount(zip.readAllBytes()), "docB должен содержать 1 страницу");
                }
            }
        }
        assertEquals(Set.of("docA.pdf", "docB.pdf", "report.json"), entries);

        HttpResponse<byte[]> docResponse = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/batch/" + jobId + "/doc/docA")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, docResponse.statusCode());
        assertEquals("application/pdf", docResponse.headers().firstValue("Content-Type").orElse(null));
        assertEquals("%PDF", new String(docResponse.body(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void submitBatchExceedingQueueCapacityReturns429WithRetryAfter() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        MultipartBuilder builder = multipart(boundary);
        for (int i = 0; i < 6; i++) { // queue-capacity=5 в этом контексте - батч из 6 не влезает целиком
            builder.filePart("page", "p" + i + ".png", "image/png", png(20, 20))
                    .textPart("docId", "doc" + i);
        }
        byte[] body = builder.build();

        HttpResponse<String> response = post("/api/v1/convert/batch", boundary, body);

        assertEquals(429, response.statusCode(), response.body());
        assertNotNull(response.headers().firstValue("Retry-After").orElse(null), "ожидали заголовок Retry-After");
    }

    @Test
    void unknownJobIdReturns404ForStatusZipAndDoc() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000000";

        assertEquals(404, get("/api/v1/convert/batch/" + unknown).statusCode());
        assertEquals(404, get("/api/v1/convert/batch/" + unknown + "/result").statusCode());
        assertEquals(404, get("/api/v1/convert/batch/" + unknown + "/doc/whatever").statusCode());
    }

    @Test
    void unknownDocIdInExistingJobReturns404() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "a.png", "image/png", png(40, 30))
                .textPart("docId", "onlyDoc")
                .build();
        HttpResponse<String> submitted = post("/api/v1/convert/batch", boundary, body);
        String jobId = extractJsonString(submitted.body(), "jobId");

        assertEquals(404, get("/api/v1/convert/batch/" + jobId + "/doc/missingDoc").statusCode());
    }

    @Test
    void downloadingDocumentBeforeItIsDoneReturns409ThenSucceedsAfterCompletion() throws Exception {
        // Занимаем оба воркера крупными страницами, чтобы гарантировать, что следующий батч
        // останется PENDING на момент немедленной проверки сразу после 202.
        String occupantBoundary = "TestBoundary" + System.nanoTime();
        byte[] occupantBody = multipart(occupantBoundary)
                .filePart("page", "big1.png", "image/png", png(1800, 1400))
                .textPart("docId", "occupant1")
                .filePart("page", "big2.png", "image/png", png(1800, 1400))
                .textPart("docId", "occupant2")
                .build();
        HttpResponse<String> occupantSubmit = post("/api/v1/convert/batch", occupantBoundary, occupantBody);
        assertEquals(202, occupantSubmit.statusCode());

        String targetBoundary = "TestBoundary" + System.nanoTime();
        byte[] targetBody = multipart(targetBoundary)
                .filePart("page", "small.png", "image/png", png(30, 20))
                .textPart("docId", "target")
                .build();
        HttpResponse<String> targetSubmit = post("/api/v1/convert/batch", targetBoundary, targetBody);
        assertEquals(202, targetSubmit.statusCode());
        String targetJobId = extractJsonString(targetSubmit.body(), "jobId");

        HttpResponse<String> tooEarly = get("/api/v1/convert/batch/" + targetJobId + "/doc/target");
        assertEquals(409, tooEarly.statusCode(),
                "документ должен быть ещё не готов, пока оба воркера заняты крупными occupant-страницами: "
                        + tooEarly.body());

        awaitJobDone(targetJobId);
        HttpResponse<byte[]> ready = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/convert/batch/" + targetJobId + "/doc/target"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, ready.statusCode());
    }

    @Test
    void submitBatchWithMismatchedPageAndDocIdCountsReturns400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "a.png", "image/png", png(40, 30))
                .filePart("page", "b.png", "image/png", png(40, 30))
                .textPart("docId", "onlyOne")
                .build();

        HttpResponse<String> response = post("/api/v1/convert/batch", boundary, body);

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    void submitBatchWithDocIdsCollidingAfterSanitizationReturns400() throws Exception {
        String boundary = "TestBoundary" + System.nanoTime();
        byte[] body = multipart(boundary)
                .filePart("page", "a.png", "image/png", png(40, 30))
                .textPart("docId", "x/y")
                .filePart("page", "b.png", "image/png", png(40, 30))
                .textPart("docId", "x\\y")
                .build();

        HttpResponse<String> response = post("/api/v1/convert/batch", boundary, body);

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("совпадают"), response.body());
    }

    private String awaitJobDone(String jobId) {
        StringBuilder lastBody = new StringBuilder();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            HttpResponse<String> status = get("/api/v1/convert/batch/" + jobId);
            lastBody.setLength(0);
            lastBody.append(status.body());
            return status.statusCode() == 200 && status.body().contains("\"jobStatus\":\"DONE\"");
        });
        return lastBody.toString();
    }

    private static int loadPageCount(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        }
    }

    private static byte[] png(int width, int height) throws Exception {
        return TestImages.encode(TestImages.colorPage(width, height), "png");
    }

    private static String extractJsonString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError("Поле '" + field + "' не найдено в JSON: " + json);
        }
        return m.group(1);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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