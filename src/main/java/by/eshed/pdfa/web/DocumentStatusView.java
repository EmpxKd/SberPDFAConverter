package by.eshed.pdfa.web;

import by.eshed.pdfa.batch.DocumentJob;

/** JSON-представление одного документа батча для статус-эндпоинта и {@code report.json} ZIP. */
public record DocumentStatusView(String docId, String status, Boolean compliant, String error) {

    public static DocumentStatusView of(DocumentJob doc) {
        return new DocumentStatusView(doc.docId(), doc.status().name(), doc.compliant(), doc.error());
    }
}
