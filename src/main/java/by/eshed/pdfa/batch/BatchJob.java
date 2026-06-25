package by.eshed.pdfa.batch;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Одно батч-задание: набор {@link DocumentJob} с общим {@code jobId}.
 * Реестр заданий ({@link BatchJobService}) хранит экземпляры in-memory (см.
 * RUNBOOK.md - рестарт сервиса теряет in-flight задания).
 */
public final class BatchJob {

    private final String jobId;
    private final Map<String, DocumentJob> documents;
    private final Instant createdAt;

    /**
     * @param jobId     уникальный идентификатор батча (UUID)
     * @param documents документы батча, порядок - порядок первого появления {@code docId} во
     *                   входном запросе; ключ - исходный (не санитизированный) {@code docId}
     */
    public BatchJob(String jobId, Map<String, DocumentJob> documents) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.documents = new LinkedHashMap<>(Objects.requireNonNull(documents, "documents"));
        if (this.documents.isEmpty()) {
            throw new IllegalArgumentException("Батч не может быть без документов");
        }
        this.createdAt = Instant.now();
    }

    public String jobId() {
        return jobId;
    }

    /** Документы батча в порядке первого появления {@code docId} во входном запросе. */
    public Collection<DocumentJob> documents() {
        return documents.values();
    }

    /** Документ по его исходному {@code docId}, если он есть в этом батче. */
    public Optional<DocumentJob> document(String docId) {
        return Optional.ofNullable(documents.get(docId));
    }

    /** Общее число документов в батче. */
    public int totalCount() {
        return documents.size();
    }

    /** Момент регистрации батча - точка отсчёта для TTL-уборки ({@code ResultCleanupTask}). */
    public Instant createdAt() {
        return createdAt;
    }

    /** {@code true}, если все документы батча вышли из {@code PENDING}/{@code RUNNING}. */
    public boolean isFinished() {
        return documents.values().stream().noneMatch(d ->
                d.status() == DocumentStatus.PENDING || d.status() == DocumentStatus.RUNNING);
    }
}
