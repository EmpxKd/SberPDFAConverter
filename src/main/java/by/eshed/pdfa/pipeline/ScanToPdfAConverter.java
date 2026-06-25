package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.core.OutputIntentFactory;
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
import by.eshed.pdfa.validation.ValidationOutcome;
import by.eshed.pdfa.validation.VeraPdfValidator;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Главный конвейер: скан(ы) -> нормализация -> сборка PDF/A -> OutputIntent ->
 * (опционально) вложение подписи -> метаданные -> обязательная проверка veraPDF.
 */
public final class ScanToPdfAConverter {

    private final float targetDpi;
    private final float jpegQuality;

    public ScanToPdfAConverter(float targetDpi, float jpegQuality) {
        this.targetDpi = targetDpi;
        this.jpegQuality = jpegQuality;
    }

    public ConversionResult convert(ConversionRequest request) {
        // Тегированный PDF (/MarkInfo, /StructTreeRoot) нужен только для 1a.
        boolean tagged = request.flavour() == PdfAFlavourOption.PDF_A_1A;
        String language = request.metadata().language() != null ? request.metadata().language() : "ru";

        PdfABuilder builder = new PdfABuilder(jpegQuality);
        PDDocument document = tagged
                ? buildTagged(builder, request.pages(), language)
                : buildStreaming(builder, request.pages());

        try (PDDocument doc = document) {
            OutputIntentFactory.addSRgbOutputIntent(doc);

            if (request.attachment() != null) {
                try {
                    AttachmentEmbedder.embed(doc, request.attachment());
                } catch (IOException e) {
                    throw new PdfAConversionException("Не удалось встроить файл подписи (PDF/A-3)", e);
                }
            }

            try {
                MetadataMapper.apply(doc, request.metadata(), request.flavour());
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
                    doc.setVersion(1.4f);
                    doc.save(out, CompressParameters.NO_COMPRESSION);
                } else {
                    doc.save(out);
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

    /**
     * Сборка для тегированного PDF/A-1a: {@code /StructTreeRoot} строится только после того, как
     * известны все страницы документа, поэтому здесь страницы декодируются заранее целиком -
     * в отличие от потоковой сборки 1b ({@link #buildStreaming}).
     */
    private PDDocument buildTagged(PdfABuilder builder, List<PageSource> sources, String language) {
        List<NormalizedPage> normalizedPages = normalizeAll(sources);
        try {
            return builder.build(normalizedPages, true, language);
        } catch (IOException e) {
            throw new PdfAConversionException("Не удалось собрать PDF из нормализованных страниц", e);
        }
    }

    /**
     * Потоковая сборка для PDF/A-1b: идёт по входным файлам по очереди - декодирует один
     * {@link PageSource}, дорисовывает его страницы в общий {@link PDDocument} и освобождает растр
     * ({@code BufferedImage#flush()}) до перехода к следующему файлу. Пик памяти на документ -
     * один декодированный файл, а не все страницы документа сразу.
     */
    private PDDocument buildStreaming(PdfABuilder builder, List<PageSource> sources) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Нет страниц для сборки PDF");
        }
        ImageNormalizer imageNormalizer = new ImageNormalizer(targetDpi);
        PdfPageRasterizer pdfRasterizer = new PdfPageRasterizer(targetDpi);
        PDDocument document = new PDDocument();
        try {
            int pageIndex = 0;
            for (PageSource source : sources) {
                List<NormalizedPage> pages;
                try {
                    pages = source.format() == SourceFormat.PDF
                            ? pdfRasterizer.readPages(source)
                            : imageNormalizer.readPages(source);
                } catch (IOException e) {
                    throw new PdfAConversionException(
                            "Не удалось прочитать входной файл (" + source.format() + ")", e);
                }
                for (NormalizedPage page : pages) {
                    try {
                        builder.addImagePage(document, page, null, pageIndex++);
                    } catch (IOException e) {
                        throw new PdfAConversionException("Не удалось собрать PDF из нормализованных страниц", e);
                    } finally {
                        page.image().flush();
                    }
                }
            }
            return document;
        } catch (RuntimeException e) {
            closeQuietly(document);
            throw e;
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

    private static void closeQuietly(PDDocument document) {
        try {
            document.close();
        } catch (IOException ignored) {
            // best-effort закрытие после уже случившейся ошибки сборки - не маскируем
            // исходную причину сбоя вторым исключением
        }
    }
}
