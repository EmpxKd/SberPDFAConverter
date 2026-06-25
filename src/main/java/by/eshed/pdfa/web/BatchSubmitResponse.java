package by.eshed.pdfa.web;

/**
 * Тело ответа {@code 202 Accepted} на {@code POST /api/v1/convert/batch}:
 * {@code jobId} для последующего опроса статуса/скачивания результата по {@code statusUrl}.
 */
public record BatchSubmitResponse(String jobId, int documents, String statusUrl) {
}
