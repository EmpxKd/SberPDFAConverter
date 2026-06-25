package by.eshed.pdfa.web;

import by.eshed.pdfa.validation.ValidationOutcome;

import java.util.List;

/**
 * JSON-представление {@link ValidationOutcome} для REST-ответов — имена полей сохранены
 * буквально по старому контракту ({@code http.HttpResponses#validationJson}), чтобы не ломать
 * существующие Postman-коллекции/клиентов СХЭД.
 */
public record ValidationReport(boolean compliant, String flavour, int totalAssertions,
                                int failedAssertions, List<String> failures) {

    public static ValidationReport of(ValidationOutcome outcome) {
        return new ValidationReport(outcome.isCompliant(), outcome.flavour().toString(),
                outcome.totalAssertions(), outcome.failedAssertions(), outcome.failureMessages());
    }
}
