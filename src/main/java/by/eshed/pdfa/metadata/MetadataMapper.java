package by.eshed.pdfa.metadata;

import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PdfAFlavourOption;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Единая точка записи метаданных: пишет одни и те же значения синхронно в docinfo и в XMP
 * (Title -> dc:title, Author -> dc:creator, Subject -> dc:description, даты -> xmp:CreateDate),
 * как требует таблица соответствия ISO 19005 (PDF/A) Info-dict <-> XMP. Поле, которое не задано
 * в {@link DocumentMetadata}, не пишется НИ В ОДНУ из схем.
 *
 * Известный баг "PDF/A_1A XMP Metadata validation fails if title and/or subject are set"
 * (описание задачи 1.txt) с этим кодом не связан: корень бага оказался не в Lang Alt структуре
 * title/subject, а в самом PDFBox-движке veraPDF, который не читает XMP Metadata при ЛЮБОМ
 * непустом /Info-словаре (см. IMPLEMENTATION_LOG.md, "...переход на Greenfield-движок") -
 * подтверждённый и закрытый баг самого veraPDF (issue #1469), устранён переходом на их
 * Greenfield-движок (org.verapdf:validation-model), а не изменением формата записи здесь.
 */
public final class MetadataMapper {

    private static final String PRODUCER = "СХЭД PDF/A Converter (Apache PDFBox)";
    private static final String DEFAULT_LANG = "x-default";

    private MetadataMapper() {
    }

    public static void apply(PDDocument document, DocumentMetadata metadata, PdfAFlavourOption flavour) throws IOException {
        PDDocumentInformation info = document.getDocumentInformation();
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        AdobePDFSchema adobe = xmp.createAndAddAdobePDFSchema();

        if (metadata.title() != null) {
            info.setTitle(metadata.title());
            dc.setTitle(DEFAULT_LANG, metadata.title());
        }
        if (metadata.author() != null) {
            info.setAuthor(metadata.author());
            dc.addCreator(metadata.author());
        }
        if (metadata.subject() != null) {
            info.setSubject(metadata.subject());
            dc.addDescription(DEFAULT_LANG, metadata.subject());
        }
        if (metadata.documentDate() != null) {
            Calendar calendar = toCalendar(metadata.documentDate());
            info.setCreationDate(calendar);
            basic.setCreateDate(calendar);
        }
        if (metadata.sourceSystem() != null) {
            basic.setCreatorTool(metadata.sourceSystem());
        }

        info.setProducer(PRODUCER);
        adobe.setProducer(PRODUCER);

        PDFAIdentificationSchema idSchema = xmp.createAndAddPDFAIdentificationSchema();
        idSchema.setPart(flavour.part());
        try {
            idSchema.setConformance(flavour.conformance());
        } catch (org.apache.xmpbox.type.BadFieldValueException e) {
            throw new IOException("Некорректное значение conformance PDF/A: " + flavour.conformance(), e);
        }

        ByteArrayOutputStream xmpBytes = new ByteArrayOutputStream();
        try {
            new XmpSerializer().serialize(xmp, xmpBytes, true);
        } catch (javax.xml.transform.TransformerException e) {
            throw new IOException("Не удалось сериализовать XMP-метаданные", e);
        }

        PDMetadata pdMetadata = new PDMetadata(document);
        pdMetadata.importXMPMetadata(xmpBytes.toByteArray());
        document.getDocumentCatalog().setMetadata(pdMetadata);
    }

    private static Calendar toCalendar(LocalDate date) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        calendar.setTimeZone(java.util.TimeZone.getTimeZone(ZoneId.systemDefault()));
        return calendar;
    }
}
