package by.eshed.pdfa.web;

import by.eshed.pdfa.batch.BatchJob;
import by.eshed.pdfa.batch.DocumentJob;

import java.util.List;

/**
 * JSON-представление статуса батча ({@code GET /api/v1/convert/batch/{jobId}})
 * и тело {@code report.json} внутри ZIP-результата - одни и те же поля, плюс верхнеуровневый
 * {@code jobStatus} ({@code RUNNING}, пока не все документы вышли из PENDING/RUNNING, иначе
 * {@code DONE} - независимо от того, были ли отдельные документы FAILED).
 */
public record BatchStatusResponse(String jobId, String jobStatus, int total, int pending, int running,
                                   int done, int failed, List<DocumentStatusView> documents) {

    public static BatchStatusResponse of(BatchJob job) {
        int pending = 0;
        int running = 0;
        int done = 0;
        int failed = 0;
        List<DocumentStatusView> views = new java.util.ArrayList<>(job.totalCount());
        for (DocumentJob doc : job.documents()) {
            views.add(DocumentStatusView.of(doc));
            switch (doc.status()) {
                case PENDING -> pending++;
                case RUNNING -> running++;
                case DONE -> done++;
                case FAILED -> failed++;
            }
        }
        String jobStatus = job.isFinished() ? "DONE" : "RUNNING";
        return new BatchStatusResponse(job.jobId(), jobStatus, job.totalCount(), pending, running, done, failed, views);
    }
}
