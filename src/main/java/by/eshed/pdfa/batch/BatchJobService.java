package by.eshed.pdfa.batch;

import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Реестр и оркестратор батч-заданий: принимает сгруппированные по {@code docId} страницы,
 * регистрирует {@link BatchJob} и ставит по одной задаче на документ в {@link ConversionExecutor}.
 * Per-документная конвертация - неизменный {@link ScanToPdfAConverter#convert(ConversionRequest)},
 * изоляция сбоев гарантируется тем, что каждый документ оборачивается собственным
 * {@code try/catch} в {@link #processDocument}.
 */
public final class BatchJobService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchJobService.class);

    private final ConversionExecutor executor;
    private final ResultStore resultStore;
    private final ScanToPdfAConverter converter;
    private final ConcurrentHashMap<String, BatchJob> registry = new ConcurrentHashMap<>();
    private final Object submitLock = new Object();

    public BatchJobService(ConversionExecutor executor, ResultStore resultStore, ScanToPdfAConverter converter) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore");
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    /**
     * Регистрирует новый батч и ставит каждый документ отдельной задачей в очередь воркеров.
     * Проверка свободного места и постановка задач происходят под одним замком: батч либо
     * влезает в очередь целиком, либо отклоняется целиком - без частичной постановки части
     * документов.
     *
     * @param groupedPages    страницы документов, сгруппированные по исходному {@code docId},
     *                        порядок - порядок первого появления {@code docId} во входном запросе
     * @param metadataFactory строит {@link DocumentMetadata} для конкретного {@code docId}
     *                        (общие поля батча + {@code title=docId}, см. контроллер)
     * @param flavour         целевой профиль PDF/A-1, общий на весь батч
     * @param strictValidation общий на батч флаг обязательности гейта veraPDF
     * @return зарегистрированный {@link BatchJob} (документы ещё не обработаны - статусы {@code PENDING})
     * @throws IllegalArgumentException если два разных {@code docId} в батче совпадают после
     *                                   санитизации {@link ResultStore#safeName} (см.
     *                                   {@link #checkNoSanitizedCollisions})
     * @throws BatchQueueFullException если в очереди воркеров не хватает места под весь батч
     */
    public BatchJob submit(Map<String, List<PageSource>> groupedPages,
                            Function<String, DocumentMetadata> metadataFactory,
                            PdfAFlavourOption flavour,
                            boolean strictValidation) {
        checkNoSanitizedCollisions(groupedPages.keySet());

        String jobId = UUID.randomUUID().toString();
        Map<String, DocumentJob> documents = new LinkedHashMap<>();
        for (Map.Entry<String, List<PageSource>> entry : groupedPages.entrySet()) {
            String docId = entry.getKey();
            documents.put(docId, new DocumentJob(docId, entry.getValue(), metadataFactory.apply(docId)));
        }
        BatchJob job = new BatchJob(jobId, documents);

        synchronized (submitLock) {
            int free = executor.remainingCapacity();
            if (free < job.totalCount()) {
                throw new BatchQueueFullException("Очередь воркеров занята: батч из " + job.totalCount()
                        + " документов не влезает в " + free + " свободных слотов, попробуйте позже");
            }
            registry.put(jobId, job);
            for (DocumentJob doc : job.documents()) {
                executor.execute(() -> processDocument(job, doc, flavour, strictValidation));
            }
        }
        LOG.info("Вход/выход: submit, jobId={}, документов={}", jobId, job.totalCount());
        return job;
    }

    /**
     * {@link ResultStore#safeName} убирает {@code / \ ..} и управляющие символы из {@code docId}
     * для имени файла на диске/в ZIP - два разных исходных {@code docId} (например {@code "a/b"}
     * и {@code "a\b"}) могли бы совпасть после санитизации и молча перезаписать результат друг
     * друга в {@link ResultStore#store}. Проверяется до регистрации батча, чтобы отклонить его
     * целиком одной понятной ошибкой, а не терять документ без следа.
     */
    private static void checkNoSanitizedCollisions(Iterable<String> docIds) {
        Map<String, String> safeNameToDocId = new HashMap<>();
        for (String docId : docIds) {
            String safe = ResultStore.safeName(docId);
            String existing = safeNameToDocId.putIfAbsent(safe, docId);
            if (existing != null) {
                throw new IllegalArgumentException("docId '" + existing + "' и '" + docId
                        + "' совпадают после санитизации имени файла ('" + safe + "') - переименуйте один из них");
            }
        }
    }

    private void processDocument(BatchJob job, DocumentJob doc, PdfAFlavourOption flavour, boolean strictValidation) {
        doc.markRunning();
        try {
            ConversionRequest request = ConversionRequest.builder()
                    .pages(doc.pages())
                    .metadata(doc.metadata())
                    .flavour(flavour)
                    .strictValidation(strictValidation)
                    .build();
            ConversionResult result = converter.convert(request);
            resultStore.store(job.jobId(), doc.docId(), result.pdfBytes());
            doc.markDone(result.validation().isCompliant());
            LOG.info("Документ готов: jobId={}, docId={}, compliant={}",
                    job.jobId(), doc.docId(), result.validation().isCompliant());
        } catch (Exception e) {
            // Граница воркера пула: один битый документ не должен ронять остальные задачи батча.
            // Без этого catch исключение ушло бы в Thread.UncaughtExceptionHandler пула, а статус
            // документа навсегда остался бы RUNNING.
            doc.markFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            LOG.warn("Документ упал: jobId={}, docId={}, причина={}", job.jobId(), doc.docId(), e.toString());
        } finally {
            // Без этого исходные байты страниц документа лежали бы в heap до TTL-уборки
            // (по умолчанию 24ч) даже после того, как PDF уже записан на диск.
            doc.releasePages();
        }
    }

    /** Батч по {@code jobId}, если он есть в реестре - без исключения на отсутствие. */
    public Optional<BatchJob> find(String jobId) {
        return Optional.ofNullable(registry.get(jobId));
    }

    /**
     * @throws BatchNotFoundException если {@code jobId} неизвестен реестру
     */
    public BatchJob require(String jobId) {
        BatchJob job = registry.get(jobId);
        if (job == null) {
            throw new BatchNotFoundException("Неизвестный jobId: " + jobId);
        }
        return job;
    }

    /** Снимок реестра для {@link ResultCleanupTask} - не модифицируемая копия. */
    public Collection<BatchJob> allJobs() {
        return List.copyOf(registry.values());
    }

    /** Убирает батч из реестра (после TTL-уборки или скачивания с {@code delete-on-download=true}). */
    public void remove(String jobId) {
        registry.remove(jobId);
    }
}
