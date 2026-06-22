package by.eshed.pdfa.model;

import org.verapdf.pdfa.flavours.PDFAFlavour;

/**
 * Целевые профили PDF/A, разрешённые конвертеру (DECISIONS.md, "Дефолты по открытым вопросам", п.1).
 * Базовый профиль для всего проекта — PDF/A-2b; PDF/A-1b оставлен для документов с требованием
 * конкретного ЛНПА; PDF/A-3b используется автоматически, когда к документу нужно приложить
 * отдельный файл подписи (п.5).
 */
public enum PdfAFlavourOption {

    PDF_A_1B(PDFAFlavour.PDFA_1_B, 1),
    PDF_A_2B(PDFAFlavour.PDFA_2_B, 2),
    PDF_A_3B(PDFAFlavour.PDFA_3_B, 3);

    private final PDFAFlavour veraPdfFlavour;
    private final int part;

    PdfAFlavourOption(PDFAFlavour veraPdfFlavour, int part) {
        this.veraPdfFlavour = veraPdfFlavour;
        this.part = part;
    }

    public PDFAFlavour veraPdfFlavour() {
        return veraPdfFlavour;
    }

    public int part() {
        return part;
    }

    public String conformance() {
        return "B";
    }

    public static PdfAFlavourOption parse(String value) {
        if (value == null || value.isBlank()) {
            return PDF_A_2B;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace("/", "_");
        for (PdfAFlavourOption option : values()) {
            if (option.name().equals(normalized) || option.name().equals("PDF_A_" + normalized)) {
                return option;
            }
        }
        throw new IllegalArgumentException("Неизвестный профиль PDF/A: " + value);
    }
}
