package by.eshed.pdfa;

import by.eshed.pdfa.http.HttpResponses;
import by.eshed.pdfa.http.PdfAHttpServer;
import by.eshed.pdfa.http.SourceFormats;
import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.ocr.OcrEngine;
import by.eshed.pdfa.ocr.TesseractOcrEngine;
import by.eshed.pdfa.pipeline.PdfAConversionException;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import by.eshed.pdfa.validation.ValidationOutcome;
import by.eshed.pdfa.validation.VeraPdfValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Точка входа: REST-сервер (основной режим использования из СХЭД, DECISIONS.md) или CLI
 * convert/validate для локальной проверки без поднятия сервера.
 */
public final class Main {

    private static final float DEFAULT_TARGET_DPI = 300f;
    private static final float DEFAULT_JPEG_QUALITY = 0.75f;

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        try {
            switch (args[0]) {
                case "serve":
                    runServe(args);
                    break;
                case "convert":
                    runConvert(args);
                    break;
                case "validate":
                    runValidate(args);
                    break;
                default:
                    printUsage();
                    System.exit(1);
            }
        } catch (PdfAConversionException e) {
            System.err.println("Ошибка конвертации: " + e.getMessage());
            if (e.validationOutcome() != null) {
                System.err.println(HttpResponses.validationJson(e.validationOutcome()));
            }
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void runServe(String[] args) throws IOException {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : envInt("PDFA_PORT", 8080);
        ScanToPdfAConverter converter = buildConverter();
        OcrEngine ocrEngine = newOcrEngine();
        PdfAHttpServer server = new PdfAHttpServer(port, converter, ocrEngine);
        server.start();
        System.out.println("PDF/A converter слушает порт " + server.port());
    }

    private static void runConvert(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Использование: convert <output.pdf> <input1> [input2 ...] [flavour]");
            System.exit(1);
            return;
        }
        Path output = Path.of(args[1]);
        List<String> inputs = new ArrayList<>();
        PdfAFlavourOption flavour = PdfAFlavourOption.PDF_A_2B;
        for (int i = 2; i < args.length; i++) {
            if (i == args.length - 1 && isFlavourToken(args[i])) {
                flavour = PdfAFlavourOption.parse(args[i]);
            } else {
                inputs.add(args[i]);
            }
        }

        List<PageSource> pages = new ArrayList<>();
        for (String input : inputs) {
            byte[] data = Files.readAllBytes(Path.of(input));
            pages.add(new PageSource(data, SourceFormats.detect(null, input)));
        }

        ConversionRequest request = ConversionRequest.builder()
                .pages(pages)
                .flavour(flavour)
                .build();

        ScanToPdfAConverter converter = buildConverter();
        ConversionResult result = converter.convert(request);
        Files.write(output, result.pdfBytes());
        System.out.println("OK: " + output + " (" + result.flavour() + ", veraPDF compliant="
                + result.validation().isCompliant() + ")");
    }

    private static void runValidate(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Использование: validate <input.pdf> [flavour]");
            System.exit(1);
            return;
        }
        PdfAFlavourOption flavour = args.length > 2 ? PdfAFlavourOption.parse(args[2]) : PdfAFlavourOption.PDF_A_2B;
        byte[] pdfBytes = Files.readAllBytes(Path.of(args[1]));
        ValidationOutcome outcome = VeraPdfValidator.validate(pdfBytes, flavour.veraPdfFlavour());
        System.out.println(HttpResponses.validationJson(outcome));
        if (!outcome.isCompliant()) {
            System.exit(2);
        }
    }

    private static ScanToPdfAConverter buildConverter() {
        float dpi = envFloat("PDFA_TARGET_DPI", DEFAULT_TARGET_DPI);
        float jpegQuality = envFloat("PDFA_JPEG_QUALITY", DEFAULT_JPEG_QUALITY);
        return new ScanToPdfAConverter(dpi, jpegQuality, newOcrEngine());
    }

    private static OcrEngine newOcrEngine() {
        return new TesseractOcrEngine(System.getenv("TESSDATA_PREFIX"));
    }

    private static boolean isFlavourToken(String token) {
        try {
            PdfAFlavourOption.parse(token);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
    }

    private static float envFloat(String name, float defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : Float.parseFloat(value.trim());
    }

    private static void printUsage() {
        System.err.println("Использование:");
        System.err.println("  serve [port]");
        System.err.println("  convert <output.pdf> <input1> [input2 ...] [flavour=1b|2b|3b]");
        System.err.println("  validate <input.pdf> [flavour=1b|2b|3b]");
    }
}
