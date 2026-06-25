package by.eshed.pdfa.batch;

import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;

import java.util.List;
import java.util.Objects;

/**
 * Один документ внутри батча: группа страниц с одинаковым {@code docId} и метаданные, с которыми
 * его конвертирует воркер пула. Статус/результат валидации/ошибка - {@code volatile}: пишет ровно
 * один поток-воркер этого документа, читает статус-эндпоинт из другого потока
 * ({@link by.eshed.pdfa.batch.BatchJobService}).
 */
public final class DocumentJob {

    private final String docId;
    private final DocumentMetadata metadata;

    /**
     * Исходные байты страниц документа - держим их только до конца попытки конвертации.
     * Реестр заданий живёт в памяти до TTL (по умолчанию 24ч), поэтому без явного освобождения
     * входные байты каждого файла лежали бы в heap всё это время даже после того, как PDF уже на
     * диске. {@link #releasePages()} обнуляет ссылку сразу после {@code markDone}/{@code
     * markFailed} - после этого момента страницы никем не читаются.
     */
    private volatile List<PageSource> pages;

    private volatile DocumentStatus status = DocumentStatus.PENDING;
    private volatile String error;
    private volatile Boolean compliant;

    /**
     * @param docId    ключ документа в батче (исходный, не санитизированный - санитизация только
     *                 для имени файла на диске, см. {@code ResultStore})
     * @param pages    упорядоченные страницы документа, не пустой список
     * @param metadata метаданные, с которыми будет вызван {@code ScanToPdfAConverter.convert}
     */
    public DocumentJob(String docId, List<PageSource> pages, DocumentMetadata metadata) {
        this.docId = Objects.requireNonNull(docId, "docId");
        this.pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (this.pages.isEmpty()) {
            throw new IllegalArgumentException("У документа '" + docId + "' нет страниц");
        }
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /** Исходный (не санитизированный) ключ документа в батче. */
    public String docId() {
        return docId;
    }

    /**
     * Упорядоченные страницы документа, как пришли в запросе.
     *
     * @throws IllegalStateException если страницы уже освобождены {@link #releasePages()} -
     *                                вызывается ровно один раз воркером, до этого вызова
     */
    public List<PageSource> pages() {
        List<PageSource> current = pages;
        if (current == null) {
            throw new IllegalStateException("Страницы документа '" + docId + "' уже освобождены после обработки");
        }
        return current;
    }

    /**
     * Освобождает ссылку на исходные байты страниц - вызывается воркером сразу после попытки
     * конвертации (успешной или нет), когда {@link #pages()} больше не нужен.
     */
    public void releasePages() {
        this.pages = null;
    }

    /** Метаданные, с которыми будет вызван {@code ScanToPdfAConverter.convert}. */
    public DocumentMetadata metadata() {
        return metadata;
    }

    public DocumentStatus status() {
        return status;
    }

    /** Переводит документ в {@link DocumentStatus#RUNNING} - вызывается воркером перед конвертацией. */
    public void markRunning() {
        this.status = DocumentStatus.RUNNING;
    }

    /** Переводит документ в {@link DocumentStatus#DONE} с итогом проверки veraPDF. */
    public void markDone(boolean compliant) {
        this.compliant = compliant;
        this.status = DocumentStatus.DONE;
    }

    /** Переводит документ в {@link DocumentStatus#FAILED} с причиной - батч продолжается. */
    public void markFailed(String error) {
        this.error = error;
        this.status = DocumentStatus.FAILED;
    }

    /** Причина сбоя, если документ {@code FAILED}; иначе {@code null}. */
    public String error() {
        return error;
    }

    /** {@code true}/{@code false} если документ {@code DONE}, иначе {@code null}. */
    public Boolean compliant() {
        return compliant;
    }
}
