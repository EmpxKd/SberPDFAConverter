package by.eshed.pdfa.batch;

import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import by.eshed.pdfa.testutil.TestImages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * PLAN.md, "Задачи для тестировщика", сценарии 1/2/3/5/7/8 — напрямую через
 * {@link BatchJobService}/{@link ConversionExecutor}/{@link ResultStore}, без HTTP-слоя
 * (как {@code pipeline.ScanToPdfAConverterTest} тестирует конвейер напрямую). HTTP-контракт
 * (202/404/409/429, multipart-группировка) — отдельно в {@code BatchConvertApiIntegrationTest}.
 */
class BatchJobServiceTest {

    private final ScanToPdfAConverter converter = new ScanToPdfAConverter(300f, 0.75f);

    @Test
    void batchOfManyDocumentsProducesCompliantPdfPerDocumentWithMatchingPageCount(@TempDir Path resultDir)
            throws Exception {
        ConversionExecutor executor = new ConversionExecutor(2, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);

        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("docA", List.of(page(50, 40), page(50, 40)));
        grouped.put("docB", List.of(page(60, 30)));
        grouped.put("docC", List.of(page(40, 40), page(40, 40), page(40, 40)));

        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);

        awaitFinished(job);

        assertEquals(3, job.totalCount());
        assertPageCountOnDisk(resultStore, job, "docA", 2);
        assertPageCountOnDisk(resultStore, job, "docB", 1);
        assertPageCountOnDisk(resultStore, job, "docC", 3);
        for (DocumentJob doc : job.documents()) {
            assertEquals(DocumentStatus.DONE, doc.status(), doc.docId() + ": " + doc.error());
            assertEquals(Boolean.TRUE, doc.compliant(), doc.docId() + " не прошёл veraPDF");
        }
        executor.shutdown();
    }

    @Test
    void differentDocIdsProduceDistinctPdfsNotMixed(@TempDir Path resultDir) throws Exception {
        ConversionExecutor executor = new ConversionExecutor(2, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);

        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("clientA", List.of(page(80, 50)));
        grouped.put("clientB", List.of(page(200, 150)));

        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);
        awaitFinished(job);

        float widthA = mediaBoxWidth(resultStore, job, "clientA");
        float widthB = mediaBoxWidth(resultStore, job, "clientB");

        assertTrue(widthA < widthB, "clientA (80px) должен дать уже страницу, чем clientB (200px): "
                + widthA + " vs " + widthB);
        assertNotEquals(widthA, widthB);
    }

    @Test
    void failureInOneDocumentIsolatedOthersSucceed(@TempDir Path resultDir) throws Exception {
        ConversionExecutor executor = new ConversionExecutor(2, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);

        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("good1", List.of(page(50, 40)));
        byte[] garbage = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        grouped.put("bad", List.of(new PageSource(garbage, SourceFormat.PNG, "bad", "garbage.png")));
        grouped.put("good2", List.of(page(60, 30)));

        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);
        awaitFinished(job);

        DocumentJob bad = job.document("bad").orElseThrow();
        assertEquals(DocumentStatus.FAILED, bad.status());
        assertNotNull(bad.error(), "причина сбоя должна быть зафиксирована");

        for (String okId : List.of("good1", "good2")) {
            DocumentJob ok = job.document(okId).orElseThrow();
            assertEquals(DocumentStatus.DONE, ok.status(), okId + ": " + ok.error());
            assertEquals(Boolean.TRUE, ok.compliant());
        }
        assertTrue(resultStore.resolve(job.jobId(), "bad").isEmpty(), "у упавшего документа не должно быть файла на диске");
        executor.shutdown();
    }

    @Test
    void submitReturnsImmediatelyWithoutWaitingForConversion(@TempDir Path resultDir) throws Exception {
        ConversionExecutor executor = new ConversionExecutor(1, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);

        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("docA", List.of(page(400, 300), page(400, 300), page(400, 300)));

        long start = System.nanoTime();
        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);
        long submitMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(submitMillis < 500, "submit() должен вернуться сразу, не дожидаясь конвертации: " + submitMillis + "ms");

        DocumentJob doc = job.document("docA").orElseThrow();
        assertTrue(doc.status() == DocumentStatus.PENDING || doc.status() == DocumentStatus.RUNNING
                        || doc.status() == DocumentStatus.DONE,
                "сразу после submit статус не должен быть неопределённым: " + doc.status());

        awaitFinished(job);
        assertEquals(DocumentStatus.DONE, job.document("docA").orElseThrow().status());
        executor.shutdown();
    }

    @Test
    void ttlCleanupRemovesFinishedBatchFromRegistryAndDisk(@TempDir Path resultDir) throws Exception {
        ConversionExecutor executor = new ConversionExecutor(2, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);
        ResultCleanupTask cleanupTask = new ResultCleanupTask(service, resultStore, 0L);

        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("docA", List.of(page(50, 40)));

        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);
        awaitFinished(job);
        assertTrue(resultStore.resolve(job.jobId(), "docA").isPresent(), "до уборки результат должен быть на диске");

        Thread.sleep(5); // TTL=0ч — нужен лишь зазор между createdAt и threshold внутри cleanup()
        cleanupTask.cleanup();

        assertTrue(service.find(job.jobId()).isEmpty(), "батч должен быть убран из реестра по TTL");
        assertTrue(resultStore.resolve(job.jobId(), "docA").isEmpty(), "каталог результатов должен быть удалён по TTL");
        executor.shutdown();
    }

    /**
     * PLAN.md, сценарий 8 ("несколько документов реально считаются параллельно, >1 воркер") -
     * через сами PDF-конвертации это ненадёжно замерить таймингом: на быстрой многоядерной машине
     * один документ обрабатывается за единицы-десятки мс, и разброс JIT/classloading на холодную
     * легко перекрывает любой выигрыш от параллелизма (так и оказалось на практике - "холодный"
     * параллельный прогон на 4 воркерах оказался медленнее "тёплого" последовательного). Поэтому
     * параллелизм проверяется детерминированно на самом {@link ConversionExecutor} (ровно то, что
     * {@link BatchJobService#submit} использует для постановки задач) синтетическими задачами с
     * фиксированной задержкой - максимум одновременно работающих задач должен быть больше 1.
     */
    @Test
    void conversionExecutorRunsTasksConcurrentlyAcrossWorkers() throws Exception {
        int workers = 4;
        int tasks = 8;
        ConversionExecutor executor = new ConversionExecutor(workers, tasks);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(tasks);

        for (int i = 0; i < tasks; i++) {
            executor.execute(() -> {
                int current = concurrent.incrementAndGet();
                maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    concurrent.decrementAndGet();
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "задачи не завершились вовремя");
        assertTrue(maxConcurrent.get() > 1,
                "ожидали параллельное выполнение (>1 воркер одновременно), наблюдали максимум " + maxConcurrent.get());
        executor.shutdown();
    }

    @Test
    void pageOrderWithinDocumentIsPreserved(@TempDir Path resultDir) throws Exception {
        ConversionExecutor executor = new ConversionExecutor(2, 10);
        ResultStore resultStore = new ResultStore(resultDir);
        BatchJobService service = new BatchJobService(executor, resultStore, converter);

        // Три явно разных по ширине страницы - проверяем, что MediaBox страниц итогового PDF
        // идёт в том же порядке, в каком страницы были переданы для этого docId.
        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        grouped.put("doc1", List.of(page(100, 80), page(300, 80), page(60, 80)));

        BatchJob job = service.submit(grouped, titleMetadataFactory(), PdfAFlavourOption.PDF_A_1B, true);
        awaitFinished(job);
        assertEquals(DocumentStatus.DONE, job.document("doc1").orElseThrow().status());

        Path pdfPath = resultStore.resolve(job.jobId(), "doc1").orElseThrow();
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdfPath))) {
            assertEquals(3, document.getNumberOfPages());
            float w1 = document.getPage(0).getMediaBox().getWidth();
            float w2 = document.getPage(1).getMediaBox().getWidth();
            float w3 = document.getPage(2).getMediaBox().getWidth();
            assertTrue(w1 < w2, "страница 1 (100px) должна быть уже страницы 2 (300px)");
            assertTrue(w3 < w1, "страница 3 (60px) должна быть уже страницы 1 (100px)");
        }
        executor.shutdown();
    }

    private static PageSource page(int width, int height) throws Exception {
        return new PageSource(TestImages.encode(TestImages.colorPage(width, height), "png"),
                SourceFormat.PNG, "ignored", "page.png");
    }

    private static Function<String, DocumentMetadata> titleMetadataFactory() {
        return docId -> DocumentMetadata.builder().title(docId).build();
    }

    private static void awaitFinished(BatchJob job) {
        await().atMost(Duration.ofSeconds(20)).until(job::isFinished);
    }

    private static void assertPageCountOnDisk(ResultStore resultStore, BatchJob job, String docId, int expectedPages)
            throws Exception {
        Path path = resultStore.resolve(job.jobId(), docId)
                .orElseThrow(() -> new AssertionError("Нет результата на диске для " + docId));
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(path))) {
            assertEquals(expectedPages, document.getNumberOfPages(), docId);
        }
    }

    private static float mediaBoxWidth(ResultStore resultStore, BatchJob job, String docId) throws Exception {
        Path path = resultStore.resolve(job.jobId(), docId).orElseThrow();
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(path))) {
            PDPage page = document.getPage(0);
            return page.getMediaBox().getWidth();
        }
    }

    private static void assertNotEquals(float a, float b) {
        if (a == b) {
            fail("Ожидали разные значения, оба равны " + a);
        }
    }
}