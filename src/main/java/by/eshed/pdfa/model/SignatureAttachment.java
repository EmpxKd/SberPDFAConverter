package by.eshed.pdfa.model;

import java.util.Objects;

/**
 * Отдельный файл подписи (ЭЦП), который нужно свести с основным документом в один воспринимаемый
 * документ. Реализовано как вложение (embedded file) внутри PDF/A-3b: подпись накладывается на
 * уже готовый PDF/A снаружи (PAdES), а отдельный файл подписи присоединяется сюда при
 * необходимости показать его как единый документ.
 */
public final class SignatureAttachment {

    private final byte[] data;
    private final String fileName;
    private final String mimeType;

    public SignatureAttachment(byte[] data, String fileName, String mimeType) {
        this.data = Objects.requireNonNull(data, "data");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.mimeType = (mimeType == null || mimeType.isBlank()) ? "application/octet-stream" : mimeType;
    }

    public byte[] data() {
        return data;
    }

    public String fileName() {
        return fileName;
    }

    public String mimeType() {
        return mimeType;
    }
}
