package by.eshed.pdfa.web;

import by.eshed.pdfa.batch.BatchDocumentNotReadyException;
import by.eshed.pdfa.batch.BatchJob;
import by.eshed.pdfa.batch.BatchJobService;
import by.eshed.pdfa.batch.BatchNotFoundException;
import by.eshed.pdfa.batch.DocumentJob;
import by.eshed.pdfa.batch.DocumentStatus;
import by.eshed.pdfa.batch.ResultStore;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SourceFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code /api/v1/convert/batch} — приём множества документов одним запросом, async-обработка
 * через пул воркеров с ограниченной очередью, выдача статуса и результатов. Контракт
 * {@code /convert/scan} ({@link ConvertController}) не меняется — это отдельный endpoint поверх
 * того же неизменного {@code ScanToPdfAConverter.convert}.
 */
@RestController
public class BatchConvertController {

    private static final Logger LOG = LoggerFactory.getLogger(BatchConvertController.class);

    private final BatchJobService batchJobService;
    private final ResultStore resultStore;
    private final ObjectMapper objectMapper;
    private final boolean deleteOnDownload;

    public BatchConvertController(BatchJobService batchJobService, ResultStore resultStore,
                                   ObjectMapper objectMapper,
                                   @Value("${pdfa.batch.delete-on-download:false}") boolean deleteOnDownload) {
        this.batchJobService = batchJobService;
        this.resultStore = resultStore;
        this.objectMapper = objectMapper;
        this.deleteOnDownload = deleteOnDownload;
    }

    /**
     * Принимает множество документов одним запросом: повторяемые файловые части {@code page} +
     * параллельный повторяемый текстовый параметр {@code docId} (i-й файл ↔ i-й {@code docId}).
     * Файлы группируются по {@code docId} в отдельные
     * документы; общие метаданные применяются к каждому, {@code title} каждого документа = его
     * {@code docId}. Обработка асинхронная — метод не ждёт конвертации.
     *
     * @return {@code 202 Accepted} с {@code jobId}/числом документов/{@code statusUrl}
     * @throws IOException              если не удалось прочитать содержимое файловой части
     * @throws IllegalArgumentException при некорректном входе (нет страниц, размер списков
     *                                   {@code page}/{@code docId} не совпадает, пустой {@code docId},
     *                                   некорректный {@code meta}/{@code documentDate}/{@code flavour})
     */
    @PostMapping(value = "/api/v1/convert/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchSubmitResponse> submitBatch(
            @RequestParam("page") List<MultipartFile> page,
            @RequestParam("docId") List<String> docId,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "documentDate", required = false) String documentDate,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "flavour", required = false) String flavour,
            @RequestParam(value = "strictValidation", required = false) Boolean strictValidation,
            @RequestParam(value = "meta", required = false) List<String> meta) throws IOException {
        LOG.info("Вход: submitBatch, файлов={}, docId={}", page.size(), docId.size());

        Map<String, List<PageSource>> grouped = groupByDocId(page, docId);
        LocalDate parsedDocumentDate = parseDocumentDate(documentDate);
        Function<String, DocumentMetadata> metadataFactory = docIdKey ->
                buildMetadata(docIdKey, author, subject, sourceSystem, documentType, parsedDocumentDate, language, meta);

        PdfAFlavourOption parsedFlavour = PdfAFlavourOption.parse(flavour);
        boolean strict = strictValidation == null || strictValidation;

        BatchJob job = batchJobService.submit(grouped, metadataFactory, parsedFlavour, strict);

        LOG.info("Выход: submitBatch, jobId={}, документов={}", job.jobId(), job.totalCount());
        return ResponseEntity.status(202).body(new BatchSubmitResponse(
                job.jobId(), job.totalCount(), "/api/v1/convert/batch/" + job.jobId()));
    }

    /**
     * @return статус батча: общие счётчики и статус/совместимость/ошибка каждого документа
     * @throws BatchNotFoundException если {@code jobId} неизвестен
     */
    @GetMapping("/api/v1/convert/batch/{jobId}")
    public ResponseEntity<BatchStatusResponse> status(@PathVariable String jobId) {
        BatchJob job = batchJobService.require(jobId);
        return ResponseEntity.ok(BatchStatusResponse.of(job));
    }

    /**
     * Отдаёт ZIP со всеми готовыми ({@code DONE}) PDF батча плюс {@code report.json} — поток
     * пишется прямо в тело ответа ({@link ZipOutputStream} поверх {@link OutputStream}), без
     * буферизации архива целиком в памяти. Незавершённый батч не отклоняется: отдаётся то, что
     * готово на момент запроса, а {@code report.json}/{@code jobStatus} честно отражают, что
     * батч ещё {@code RUNNING} — клиент может скачать ZIP повторно позже за тем же {@code jobId}.
     *
     * @throws BatchNotFoundException если {@code jobId} неизвестен
     */
    @GetMapping("/api/v1/convert/batch/{jobId}/result")
    public ResponseEntity<StreamingResponseBody> downloadResult(@PathVariable String jobId) {
        BatchJob job = batchJobService.require(jobId);
        LOG.info("Вход: downloadResult, jobId={}, jobStatus={}", jobId, job.isFinished() ? "DONE" : "RUNNING");
        StreamingResponseBody body = outputStream -> writeZip(job, outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header("Content-Disposition", "attachment; filename=\"" + ResultStore.safeName(jobId) + ".zip\"")
                .body(body);
    }

    /**
     * Отдаёт отдельный PDF одного документа батча.
     *
     * @throws BatchNotFoundException        если {@code jobId} или {@code docId} неизвестны
     * @throws BatchDocumentNotReadyException если документ существует, но ещё не {@code DONE}
     */
    @GetMapping("/api/v1/convert/batch/{jobId}/doc/{docId}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String jobId, @PathVariable String docId)
            throws IOException {
        BatchJob job = batchJobService.require(jobId);
        DocumentJob doc = job.document(docId)
                .orElseThrow(() -> new BatchNotFoundException("Неизвестный docId '" + docId + "' в батче " + jobId));
        if (doc.status() != DocumentStatus.DONE) {
            throw new BatchDocumentNotReadyException("Документ '" + docId + "' не готов: статус " + doc.status()
                    + (doc.error() != null ? " (" + doc.error() + ")" : ""));
        }
        Path path = resultStore.resolve(jobId, docId)
                .orElseThrow(() -> new BatchNotFoundException("Результат документа '" + docId + "' не найден на диске"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(Files.readAllBytes(path));
    }

    private void writeZip(BatchJob job, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (DocumentJob doc : job.documents()) {
                if (doc.status() != DocumentStatus.DONE) {
                    continue;
                }
                Optional<Path> path = resultStore.resolve(job.jobId(), doc.docId());
                if (path.isEmpty()) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(ResultStore.safeName(doc.docId()) + ".pdf"));
                Files.copy(path.get(), zip);
                zip.closeEntry();
            }
            // writeValueAsBytes, не writeValue(zip, ...): Jackson по умолчанию закрывает целевой
            // OutputStream после записи (AUTO_CLOSE_TARGET) - это закрыло бы весь ZipOutputStream
            // до того, как вызывающий код успеет сам его закрыть через try-with-resources.
            zip.putNextEntry(new ZipEntry("report.json"));
            zip.write(objectMapper.writeValueAsBytes(BatchStatusResponse.of(job)));
            zip.closeEntry();
        }

        if (deleteOnDownload && job.isFinished()) {
            try {
                resultStore.deleteJob(job.jobId());
                batchJobService.remove(job.jobId());
            } catch (IOException e) {
                LOG.warn("Не удалось удалить каталог батча после скачивания: jobId={}", job.jobId(), e);
            }
        }
    }

    private static Map<String, List<PageSource>> groupByDocId(List<MultipartFile> page, List<String> docId)
            throws IOException {
        if (page.isEmpty()) {
            throw new IllegalArgumentException("Не передано ни одной страницы (поле 'page')");
        }
        if (docId.size() != page.size()) {
            throw new IllegalArgumentException("Число 'docId' (" + docId.size()
                    + ") должно совпадать с числом 'page' (" + page.size() + ")");
        }
        Map<String, List<PageSource>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < page.size(); i++) {
            MultipartFile file = page.get(i);
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Пустая файловая часть 'page' на позиции " + i);
            }
            String rawDocId = docId.get(i) == null ? "" : docId.get(i).trim();
            if (rawDocId.isEmpty()) {
                throw new IllegalArgumentException("Пустой 'docId' на позиции " + i);
            }
            SourceFormat format = SourceFormats.detect(file.getContentType(), file.getOriginalFilename());
            grouped.computeIfAbsent(rawDocId, key -> new ArrayList<>())
                    .add(new PageSource(file.getBytes(), format, rawDocId, file.getOriginalFilename()));
        }
        return grouped;
    }

    private static LocalDate parseDocumentDate(String documentDate) {
        if (documentDate == null || documentDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(documentDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Некорректная дата 'documentDate': " + documentDate, e);
        }
    }

    private static DocumentMetadata buildMetadata(String docId, String author, String subject, String sourceSystem,
                                                    String documentType, LocalDate documentDate, String language,
                                                    List<String> meta) {
        DocumentMetadata.Builder builder = DocumentMetadata.builder()
                .title(docId)
                .author(author)
                .subject(subject)
                .sourceSystem(sourceSystem)
                .documentType(documentType)
                .language(language);
        if (documentDate != null) {
            builder.documentDate(documentDate);
        }
        if (meta != null) {
            for (String entry : meta) {
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException(
                            "Поле 'meta' должно быть в формате 'Ключ=Значение': " + entry);
                }
                builder.customProperty(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        return builder.build();
    }
}
