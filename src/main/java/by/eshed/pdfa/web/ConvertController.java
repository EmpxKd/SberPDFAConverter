package by.eshed.pdfa.web;

import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code POST /api/v1/convert/scan} — multipart/form-data: одна или несколько файловых частей
 * {@code page}/{@code pages} (порядок сохраняется, многостраничный TIFF и scanner-PDF
 * разворачиваются в несколько страниц), текстовые поля метаданных карточки СХЭД и повторяемое
 * поле {@code meta} (формат {@code Ключ=Значение}) для произвольных свойств заказчика. Целевой
 * формат — строго PDF/A-1 ({@code 1a}/{@code 1b}, см. {@link PdfAFlavourOption#parse}); часть 1
 * стандарта запрещает вложенные файлы, поэтому файловая часть {@code signature} отклоняется.
 */
@RestController
public class ConvertController {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertController.class);

    private final ScanToPdfAConverter converter;

    public ConvertController(ScanToPdfAConverter converter) {
        this.converter = converter;
    }

    /**
     * Конвертирует один или несколько файлов скана в PDF/A-1.
     *
     * @return байты готового PDF с {@code Content-Type: application/pdf}
     * @throws IOException              если не удалось прочитать содержимое файловой части
     * @throws IllegalArgumentException при некорректном входе (нет страниц, вложение подписи,
     *                                   неподдерживаемый флейвор, некорректный {@code meta})
     */
    @PostMapping(value = "/api/v1/convert/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convertScan(
            @RequestParam(value = "page", required = false) List<MultipartFile> page,
            @RequestParam(value = "pages", required = false) List<MultipartFile> pages,
            @RequestParam(value = "signature", required = false) MultipartFile signature,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "documentDate", required = false) String documentDate,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "flavour", required = false) String flavour,
            @RequestParam(value = "strictValidation", required = false) Boolean strictValidation,
            @RequestParam(value = "meta", required = false) List<String> meta) throws IOException {
        LOG.info("Вход: convertScan, страниц page={}, pages={}, meta={}",
                page == null ? 0 : page.size(), pages == null ? 0 : pages.size(), meta == null ? 0 : meta.size());

        if (signature != null && !signature.isEmpty()) {
            throw new IllegalArgumentException(
                    "Вложение подписи не поддерживается: целевой формат PDF/A-1 запрещает вложенные файлы");
        }

        ConversionRequest request = buildRequest(page, pages, title, author, subject, sourceSystem,
                documentType, documentDate, language, flavour, strictValidation, meta);

        ConversionResult result = converter.convert(request);

        LOG.info("Выход: convertScan, flavour={}, compliant={}, bytes={}",
                result.flavour(), result.validation().isCompliant(), result.pdfBytes().length);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.pdfBytes());
    }

    private ConversionRequest buildRequest(List<MultipartFile> page, List<MultipartFile> pages,
                                            String title, String author, String subject, String sourceSystem,
                                            String documentType, String documentDate, String language,
                                            String flavour, Boolean strictValidation,
                                            List<String> meta) throws IOException {
        List<PageSource> sources = new ArrayList<>();
        appendPages(sources, page);
        appendPages(sources, pages);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Не передано ни одной страницы (поле 'page')");
        }

        DocumentMetadata.Builder metadataBuilder = DocumentMetadata.builder()
                .title(title)
                .author(author)
                .subject(subject)
                .sourceSystem(sourceSystem)
                .documentType(documentType)
                .language(language);
        if (documentDate != null && !documentDate.isBlank()) {
            metadataBuilder.documentDate(LocalDate.parse(documentDate));
        }
        if (meta != null) {
            for (String entry : meta) {
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException(
                            "Поле 'meta' должно быть в формате 'Ключ=Значение': " + entry);
                }
                metadataBuilder.customProperty(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }

        ConversionRequest.Builder requestBuilder = ConversionRequest.builder()
                .pages(sources)
                .metadata(metadataBuilder.build())
                .flavour(PdfAFlavourOption.parse(flavour));
        if (strictValidation != null) {
            requestBuilder.strictValidation(strictValidation);
        }
        return requestBuilder.build();
    }

    private static void appendPages(List<PageSource> sources, List<MultipartFile> parts) throws IOException {
        if (parts == null) {
            return;
        }
        for (MultipartFile part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            SourceFormat format = SourceFormats.detect(part.getContentType(), part.getOriginalFilename());
            sources.add(new PageSource(part.getBytes(), format));
        }
    }
}
