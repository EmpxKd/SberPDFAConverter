package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.testutil.TestImages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сквозная проверка обязательного требования DECISIONS.md/PLAN.md: выход конвертера ВСЕГДА
 * проходит veraPDF как валидный PDF/A-1 (1a или 1b — никакой другой профиль не достижим, см.
 * {@code model.ConversionRequestTest} и {@code model.PdfAFlavourOptionTest} для проверки самого
 * запрета).
 */
class ScanToPdfAConverterTest {

    private final ScanToPdfAConverter converter = new ScanToPdfAConverter(300f, 0.75f);

    @Test
    void convertsBilevelScanToCompliantPdfA1bByDefault() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(400, 300), "png");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .metadata(DocumentMetadata.builder().title("Тестовый документ").author("Тестировщик").build())
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_1B, result.flavour());
    }

    @Test
    void convertsColorJpegScanToCompliantPdfA1bWhenExplicitlyRequested() throws Exception {
        byte[] jpeg = TestImages.encode(TestImages.colorPage(400, 300), "JPEG");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(jpeg, SourceFormat.JPEG)))
                .flavour(PdfAFlavourOption.PDF_A_1B)
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_1B, result.flavour());
    }

    @Test
    void convertsMultiPageTiffScanToCompliantPdfA1b() throws Exception {
        byte[] tiff = TestImages.encodeMultiPageTiff(
                List.of(TestImages.bilevelPage(200, 150), TestImages.bilevelPage(200, 150)));
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(tiff, SourceFormat.TIFF)))
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_1B, result.flavour());
    }

    @Test
    void convertsRasterizedPdfInputToCompliantPdfA1b() throws Exception {
        // Сначала собираем обычный скан, чтобы получить заведомо валидный входной PDF "от сканера" -
        // PdfPageRasterizer (image.PdfPageRasterizer) растеризует его страницы заново, как требует
        // описание задачи 1.txt п.3.
        byte[] png = TestImages.encode(TestImages.bilevelPage(300, 200), "png");
        ConversionRequest firstPass = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .build();
        byte[] scannerPdf = converter.convert(firstPass).pdfBytes();

        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(scannerPdf, SourceFormat.PDF)))
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_1B, result.flavour());
    }

    @Test
    void convertsScanToCompliantTaggedPdfA1a() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(300, 200), "png");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .flavour(PdfAFlavourOption.PDF_A_1A)
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
        assertEquals(PdfAFlavourOption.PDF_A_1A, result.flavour());
    }

    @Test
    void wrapsCorruptInputAsConversionExceptionInsteadOfCrashing() {
        byte[] garbage = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(garbage, SourceFormat.PNG)))
                .build();

        assertThrows(PdfAConversionException.class, () -> converter.convert(request));
    }

    @Test
    void convertsSinglePixelPageToCompliantPdfA1b() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(1, 1), "png");
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(png, SourceFormat.PNG)))
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
    }

    @Test
    void convertsLargeInputFileToCompliantPdfA1b() throws Exception {
        // BMP несжатый: 2400x1600x3 байта ~ 11.5MB - заведомо больше порога ">10MB" из ТЗ тестировщика.
        byte[] bmp = TestImages.encodeUncompressedBmp(TestImages.colorPage(2400, 1600));
        assertTrue(bmp.length > 10 * 1024 * 1024, "входной файл должен быть больше 10MB: " + bmp.length);

        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(bmp, SourceFormat.BMP)))
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
    }

    @Test
    void convertsNonStandardDpiScanToCompliantPdfA1b() throws Exception {
        // Источник заявляет 150dpi, конвейер нормализует к целевому targetDpi=300 - страница должна
        // и пройти ресемплинг (image.ImageNormalizerTest проверяет это изолированно), и остаться валидной.
        byte[] tiff = TestImages.encodeTiffWithDpi(TestImages.colorPage(100, 80), 150f);
        ConversionRequest request = ConversionRequest.builder()
                .pages(List.of(new PageSource(tiff, SourceFormat.TIFF)))
                .build();

        ConversionResult result = converter.convert(request);

        assertTrue(result.validation().isCompliant(),
                () -> "veraPDF failures: " + result.validation().failureMessages());
    }
}
