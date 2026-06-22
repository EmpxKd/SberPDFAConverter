package by.eshed.pdfa.http;

import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SignatureAttachment;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.pipeline.PdfAConversionException;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST /api/v1/convert/scan - multipart/form-data: одна или несколько файловых частей "page"
 * (порядок сохраняется, многостраничный TIFF и scanner-PDF разворачиваются в несколько страниц),
 * опциональная файловая часть "signature" (поднимает профиль до PDF/A-3b, DECISIONS.md п.5),
 * и текстовые поля метаданных карточки СХЭД (title/author/subject/sourceSystem/documentType/
 * documentDate/flavour/ocrLanguage/strictValidation).
 */
public final class ConvertScanHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(ConvertScanHandler.class.getName());

    private final ScanToPdfAConverter converter;

    public ConvertScanHandler(ScanToPdfAConverter converter) {
        this.converter = converter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponses.sendError(exchange, 405, "Только POST");
            return;
        }
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                HttpResponses.sendError(exchange, 400, "Ожидается multipart/form-data с boundary");
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            List<MultipartParser.Part> parts = MultipartParser.parse(body, boundary);

            ConversionRequest request = buildRequest(parts);
            ConversionResult result = converter.convert(request);

            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, result.pdfBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(result.pdfBytes());
            }
        } catch (IllegalArgumentException badInput) {
            HttpResponses.sendError(exchange, 400, badInput.getMessage());
        } catch (PdfAConversionException conversionFailure) {
            if (conversionFailure.validationOutcome() != null) {
                HttpResponses.sendValidationFailure(exchange, conversionFailure.validationOutcome());
            } else {
                LOG.log(Level.WARNING, "Сбой конвертации", conversionFailure);
                HttpResponses.sendError(exchange, 500, conversionFailure.getMessage());
            }
        } catch (RuntimeException unexpected) {
            LOG.log(Level.SEVERE, "Необработанная ошибка конвертации", unexpected);
            HttpResponses.sendError(exchange, 500, "Внутренняя ошибка сервера");
        }
    }

    private ConversionRequest buildRequest(List<MultipartParser.Part> parts) {
        ConversionRequest.Builder builder = ConversionRequest.builder();
        DocumentMetadata.Builder metadata = DocumentMetadata.builder();
        List<PageSource> pages = new ArrayList<>();

        for (MultipartParser.Part part : parts) {
            String name = part.name() == null ? "" : part.name();
            if (part.isFile()) {
                if ("page".equals(name) || "pages".equals(name)) {
                    SourceFormat format = SourceFormats.detect(part.contentType(), part.filename());
                    pages.add(new PageSource(part.data(), format));
                } else if ("signature".equals(name)) {
                    builder.attachment(new SignatureAttachment(part.data(), part.filename(), part.contentType()));
                }
                continue;
            }
            String value = new String(part.data(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                continue;
            }
            switch (name) {
                case "title":
                    metadata.title(value);
                    break;
                case "author":
                    metadata.author(value);
                    break;
                case "subject":
                    metadata.subject(value);
                    break;
                case "sourceSystem":
                    metadata.sourceSystem(value);
                    break;
                case "documentType":
                    metadata.documentType(value);
                    break;
                case "documentDate":
                    metadata.documentDate(LocalDate.parse(value));
                    break;
                case "flavour":
                    builder.flavour(PdfAFlavourOption.parse(value));
                    break;
                case "ocrLanguage":
                    builder.ocrLanguage(value);
                    break;
                case "ocrEnabled":
                    builder.ocrEnabled(Boolean.parseBoolean(value));
                    break;
                case "strictValidation":
                    builder.strictValidation(Boolean.parseBoolean(value));
                    break;
                default:
                    // неизвестное поле формы - игнорируем, не строгий контракт
            }
        }

        if (pages.isEmpty()) {
            throw new IllegalArgumentException("Не передано ни одной страницы (поле 'page')");
        }
        return builder.pages(pages).metadata(metadata.build()).build();
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            return null;
        }
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                String value = trimmed.substring("boundary=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }
}
