package by.eshed.pdfa.model;

import org.verapdf.pdfa.flavours.PDFAFlavour;

/**
 * Целевые профили PDF/A, разрешённые конвертеру. Достижимое множество — строго часть 1 стандарта,
 * {@code 1a}/{@code 1b}; никакой другой профиль не принимается ни через REST, ни через CLI.
 * Дефолт — {@link #PDF_A_1B} (для скана достаточно), {@link #PDF_A_1A} — по явному запросу. Часть 1
 * стандарта запрещает вложенные файлы (/EmbeddedFiles), поэтому подпись отдельным файлом и
 * профили 2/3 недостижимы (см. {@link by.eshed.pdfa.model.ConversionRequest}). Значения
 * {@code PDF_A_2B}/{@code PDF_A_3B} оставлены в enum намеренно как мёртвый код — недостижимы
 * через {@link #parse(String)}.
 */
public enum PdfAFlavourOption {

    PDF_A_1A(PDFAFlavour.PDFA_1_A, 1, "A"),
    PDF_A_1B(PDFAFlavour.PDFA_1_B, 1, "B"),
    PDF_A_2B(PDFAFlavour.PDFA_2_B, 2, "B"),
    PDF_A_3B(PDFAFlavour.PDFA_3_B, 3, "B");

    private final PDFAFlavour veraPdfFlavour;
    private final int part;
    private final String conformance;

    PdfAFlavourOption(PDFAFlavour veraPdfFlavour, int part, String conformance) {
        this.veraPdfFlavour = veraPdfFlavour;
        this.part = part;
        this.conformance = conformance;
    }

    public PDFAFlavour veraPdfFlavour() {
        return veraPdfFlavour;
    }

    public int part() {
        return part;
    }

    public String conformance() {
        return conformance;
    }

    /**
     * Разбирает значение профиля из REST-формы/CLI. Принимает только {@code 1a}/{@code 1b}
     * в произвольной нотации ({@code 1A}, {@code PDFA-1A}, {@code pdf/a-1b}, {@code PDF_A_1B}
     * и т.п. — сравнение идёт по буквенно-цифровому суффиксу после нормализации). Пустое/{@code
     * null} значение — дефолт {@link #PDF_A_1B}. Любой другой профиль (2a/2b/3a/3b и неизвестные
     * значения) отклоняется: конвертер поддерживает только часть 1 стандарта.
     *
     * @param value исходная строка профиля или {@code null}
     * @return {@link #PDF_A_1A} или {@link #PDF_A_1B}
     * @throws IllegalArgumentException если значение не соответствует PDF/A-1a или PDF/A-1b
     */
    public static PdfAFlavourOption parse(String value) {
        if (value == null || value.isBlank()) {
            return PDF_A_1B;
        }
        String normalized = value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (normalized.endsWith("1A")) {
            return PDF_A_1A;
        }
        if (normalized.endsWith("1B")) {
            return PDF_A_1B;
        }
        throw new IllegalArgumentException("Поддерживается только PDF/A-1 (1a или 1b): " + value);
    }
}
