package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SignatureAttachment;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.ocr.TesseractOcrEngine;
import by.eshed.pdfa.testutil.TestImages;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сквозная проверка обязательного требования DECISIONS.md: выход конвертера ВСЕГДА проходит
 * veraPDF как валидный PDF/A-Xb. Tesseract в этом окружении может быть не установлен (нативная
 * зависимость) - тест с OCR пропускается, если он недоступен, остальные шаги конвейера проверяются
 * без него (ocrEnabled=false).
 */
class ScanToPdfAConverterTest {

    private final TesseractOcrEngine ocrEngine = new TesseractOcrEngine(System.getenv("TESSDATA_PREFIX"));
    private final ScanToPdfAConverter converter = new ScanToPdfAConverter(300f, 0.75f, ocrEngine);

    @Test
    void convertsBilevelScanToCompliantPdfA2bWithoutOcr() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(400, 300), "png");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .ocrEnabled(false)
                .metadata(DocumentMetadata.builder().title("Тестовый документ").author("Тестировщик").build())
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_2B, result.flavour());
    }

    @Test
    void convertsColorScanWithOcrWhenTesseractAvailable() throws Exception {
        Assumptions.assumeTrue(ocrEngine.isAvailable(), "Tesseract не установлен в этом окружении - тест пропущен");

        byte[] jpeg = TestImages.encode(TestImages.colorPage(400, 300), "JPEG");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(jpeg, SourceFormat.JPEG)))
                .ocrEnabled(true)
                .ocrLanguage("eng")
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
    }

    @Test
    void embedsSignatureAttachmentAndUpgradesToPdfA3b() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(400, 300), "png");
        SignatureAttachment attachment = new SignatureAttachment(
                "dummy-signature-bytes".getBytes(StandardCharsets.UTF_8), "signature.sig", "application/octet-stream");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .ocrEnabled(false)
                .attachment(attachment)
                .build();

        ConversionResult result = converter.convert(request);

        assertEquals(PdfAFlavourOption.PDF_A_3B, result.flavour());
        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
    }
}
