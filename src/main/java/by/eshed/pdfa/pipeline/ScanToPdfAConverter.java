package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.core.BuiltDocument;
import by.eshed.pdfa.core.OutputIntentFactory;
import by.eshed.pdfa.core.PageBuildInfo;
import by.eshed.pdfa.core.PdfABuilder;
import by.eshed.pdfa.image.ImageNormalizer;
import by.eshed.pdfa.image.NormalizedPage;
import by.eshed.pdfa.image.PdfPageRasterizer;
import by.eshed.pdfa.metadata.MetadataMapper;
import by.eshed.pdfa.model.ConversionRequest;
import by.eshed.pdfa.model.ConversionResult;
import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.PdfAFlavourOption;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.ocr.EmbeddedFonts;
import by.eshed.pdfa.ocr.InvisibleTextLayerWriter;
import by.eshed.pdfa.ocr.OcrEngine;
import by.eshed.pdfa.ocr.OcrUnavailableException;
import by.eshed.pdfa.ocr.RecognizedWord;
import by.eshed.pdfa.validation.ValidationOutcome;
import by.eshed.pdfa.validation.VeraPdfValidator;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Главный конвейер: скан(ы) -> нормализация -> сборка PDF/A -> OutputIntent -> OCR-слой ->
 * (опционально) вложение подписи -> метаданные -> обязательная проверка veraPDF.
 * Порядок шагов и состав см. DECISIONS.md "Итоговая рекомендация: Вариант А".
 */
public final class ScanToPdfAConverter {

    private static final Logger LOG = Logger.getLogger(ScanToPdfAConverter.class.getName());

    private final float targetDpi;
    private final float jpegQuality;
    private final OcrEngine ocrEngine;

    public ScanToPdfAConverter(float targetDpi, float jpegQuality, OcrEngine ocrEngine) {
        this.targetDpi = targetDpi;
        this.jpegQuality = jpegQuality;
        this.ocrEngine = ocrEngine;
    }

    public ConversionResult convert(ConversionRequest request) {
        List<NormalizedPage> normalizedPages = normalizeAll(request.pages());

        // PLAN.md, задача 5: тегированный PDF (/MarkInfo, /StructTreeRoot) нужен только для 1a -
        // 1b и выше его не требуют и не должны получать лишнюю структуру.
        boolean tagged = request.flavour() == PdfAFlavourOption.PDF_A_1A;
        String language = request.metadata().language() != null ? request.metadata().language() : "ru";

        PdfABuilder builder = new PdfABuilder(jpegQuality);
        BuiltDocument built;
        try {
            built = builder.build(normalizedPages, tagged, language);
        } catch (IOException e) {
            throw new PdfAConversionException("Не удалось собрать PDF из нормализованных страниц", e);
        }

        try (PDDocument document = built.document()) {
            OutputIntentFactory.addSRgbOutputIntent(document);

            if (request.ocrEnabled()) {
                applyOcr(document, built.pages(), request.ocrLanguage());
            } else {
                // DECISIONS.md п.2: OCR обязателен всегда. Отключение оставлено только как
                // диагностический выход для сред без установленного Tesseract (например тестовых) -
                // явно логируем отступление от стандартного режима, чтобы это не прошло незамеченным.
                LOG.warning("OCR отключён для конвертации - отступление от обязательного режима (DECISIONS.md п.2)");
            }

            if (request.attachment() != null) {
                try {
                    AttachmentEmbedder.embed(document, request.attachment());
                } catch (IOException e) {
                    throw new PdfAConversionException("Не удалось встроить файл подписи (PDF/A-3)", e);
                }
            }

            try {
                MetadataMapper.apply(document, request.metadata(), request.flavour());
            } catch (IOException e) {
                throw new PdfAConversionException("Не удалось записать метаданные docinfo/XMP", e);
            }

            byte[] pdfBytes;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (request.flavour().part() == 1) {
                    // PDF/A-1 наследует ограничения PDF 1.4 (ISO 19005-1, 6.1.4): кросс-секционные
                    // потоки (xref streams) и компрессированные потоки объектов появились только
                    // в PDF 1.5 и на части 1 запрещены. PDFBox 3.x по умолчанию (save без
                    // параметров) всегда пишет их - нужно явно понизить версию и выключить сжатие.
                    document.setVersion(1.4f);
                    document.save(out, CompressParameters.NO_COMPRESSION);
                } else {
                    document.save(out);
                }
                pdfBytes = out.toByteArray();
            } catch (IOException e) {
                throw new PdfAConversionException("Не удалось сохранить итоговый PDF", e);
            }

            ValidationOutcome outcome;
            try {
                outcome = VeraPdfValidator.validate(pdfBytes, request.flavour().veraPdfFlavour());
            } catch (IOException e) {
                throw new PdfAConversionException("Ошибка прогона veraPDF", e);
            }

            if (request.strictValidation() && !outcome.isCompliant()) {
                throw new PdfAConversionException(
                        "Результат не прошёл обязательную проверку veraPDF на соответствие "
                                + request.flavour(), outcome);
            }

            return new ConversionResult(pdfBytes, request.flavour(), outcome);
        } catch (IOException e) {
            throw new PdfAConversionException("Не удалось закрыть PDDocument", e);
        }
    }

    private List<NormalizedPage> normalizeAll(List<PageSource> sources) {
        ImageNormalizer imageNormalizer = new ImageNormalizer(targetDpi);
        PdfPageRasterizer pdfRasterizer = new PdfPageRasterizer(targetDpi);
        List<NormalizedPage> pages = new ArrayList<>();
        for (PageSource source : sources) {
            try {
                if (source.format() == SourceFormat.PDF) {
                    pages.addAll(pdfRasterizer.readPages(source));
                } else {
                    pages.addAll(imageNormalizer.readPages(source));
                }
            } catch (IOException e) {
                throw new PdfAConversionException("Не удалось прочитать входной файл (" + source.format() + ")", e);
            }
        }
        return pages;
    }

    private void applyOcr(PDDocument document, List<PageBuildInfo> pages, String language) {
        PDFont ocrFont;
        try {
            ocrFont = EmbeddedFonts.loadOcrFont(document);
        } catch (IOException e) {
            throw new PdfAConversionException("Не удалось загрузить встраиваемый шрифт для OCR-слоя", e);
        }

        for (PageBuildInfo pageInfo : pages) {
            List<RecognizedWord> words;
            try {
                words = ocrEngine.recognizeWords(pageInfo.source().image(), language);
            } catch (OcrUnavailableException e) {
                throw new PdfAConversionException("Tesseract OCR недоступен - конвертация без OCR запрещена "
                        + "(DECISIONS.md п.2)", e);
            }
            try {
                InvisibleTextLayerWriter.write(document, pageInfo, words, ocrFont);
            } catch (IOException e) {
                throw new PdfAConversionException("Не удалось записать невидимый текстовый слой OCR", e);
            }
        }
    }
}
