package by.eshed.pdfa.metadata;

import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PdfAFlavourOption;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAExtensionSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.schema.XMPSchema;
import org.apache.xmpbox.type.ArrayProperty;
import org.apache.xmpbox.type.Cardinality;
import org.apache.xmpbox.type.ChoiceType;
import org.apache.xmpbox.type.PDFAPropertyType;
import org.apache.xmpbox.type.PDFASchemaType;
import org.apache.xmpbox.type.URIType;
import org.apache.xmpbox.xml.XmpSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;

/**
 * Единая точка записи метаданных: пишет одни и те же значения синхронно в docinfo и в XMP
 * (Title -> dc:title, Author -> dc:creator, Subject -> dc:description, даты -> xmp:CreateDate),
 * как требует таблица соответствия ISO 19005 (PDF/A) Info-dict <-> XMP. Поле, которое не задано
 * в {@link DocumentMetadata}, не пишется НИ В ОДНУ из схем.
 *
 * Произвольные метаданные заказчика ({@link DocumentMetadata#customProperties()}) пишутся через
 * PDF/A extension schema (ISO 19005-1 §6.6.2.3 "Extension schemas") — часть 1 стандарта не
 * допускает свойств вне предопределённых схем без декларации в {@code pdfaExtension:schemas},
 * иначе veraPDF бракует файл.
 */
public final class MetadataMapper {

    private static final String PRODUCER = "СХЭД PDF/A Converter (Apache PDFBox)";
    private static final String DEFAULT_LANG = "x-default";

    /** Namespace произвольных свойств заказчика, объявляется через pdfaExtension:schemas. */
    private static final String CUSTOM_NAMESPACE_URI = "http://eshed.by/pdfa/custom/1.0/";
    private static final String CUSTOM_NAMESPACE_PREFIX = "cust";
    private static final String CUSTOM_SCHEMA_DESCRIPTION =
            "Произвольные метаданные, переданные при конвертации (СХЭД PDF/A Converter)";
    private static final String CUSTOM_PROPERTY_LOCAL_NAME_PREFIX = "custom";

    /**
     * Дублировать ли произвольные свойства также в {@code /Info} (docinfo), не только в XMP
     * extension schema. Включено, потому что соответствие XMP-свойству по
     * {@link PDFAPropertyType#DESCRIPTION} делает нестандартный ключ в /Info допустимым по
     * ISO 19005-1 §6.7.3 — без дублирования veraPDF бракует файл.
     */
    private static final boolean DUPLICATE_CUSTOM_PROPERTIES_TO_INFO = true;

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

        if (!metadata.customProperties().isEmpty()) {
            applyCustomProperties(info, xmp, metadata.customProperties());
        }

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

    /**
     * Записывает произвольные свойства заказчика в выделенный namespace {@value #CUSTOM_NAMESPACE_URI}
     * (префикс {@value #CUSTOM_NAMESPACE_PREFIX}), декларируя его через PDF/A extension schema
     * ({@code pdfaExtension:schemas}) — этого требует ISO 19005-1, иначе veraPDF не находит схему
     * для непредопределённого свойства и бракует файл. Имена ключей заказчика (могут быть кириллицей
     * с пробелами) не валидны как имена XML-элементов, поэтому в самой схеме используется безопасный
     * сгенерированный local name ({@value #CUSTOM_PROPERTY_LOCAL_NAME_PREFIX}1, 2…), а человекочитаемый
     * ключ заказчика идёт в {@code pdfaProperty:description}.
     *
     * @param info             docinfo текущего документа (для опционального дублирования значений)
     * @param xmp              XMP-метаданные, в которые добавляется extension schema и схема свойств
     * @param customProperties непустая карта "ключ-значение" в порядке вставки
     */
    private static void applyCustomProperties(PDDocumentInformation info, XMPMetadata xmp,
                                                Map<String, String> customProperties) {
        PDFAExtensionSchema extensionSchema = xmp.createAndAddPDFAExtensionSchemaWithDefaultNS();
        ArrayProperty schemasBag = extensionSchema.createArrayProperty(PDFAExtensionSchema.SCHEMAS, Cardinality.Bag);
        extensionSchema.addProperty(schemasBag);

        PDFASchemaType schemaType = new PDFASchemaType(xmp);
        schemaType.addProperty(schemaType.createTextType(PDFASchemaType.SCHEMA, CUSTOM_SCHEMA_DESCRIPTION));
        schemaType.addProperty(new URIType(xmp, schemaType.getNamespace(), schemaType.getPrefix(),
                PDFASchemaType.NAMESPACE_URI, CUSTOM_NAMESPACE_URI));
        schemaType.addProperty(schemaType.createTextType(PDFASchemaType.PREFIX, CUSTOM_NAMESPACE_PREFIX));
        ArrayProperty propertySeq = schemaType.createArrayProperty(PDFASchemaType.PROPERTY, Cardinality.Seq);
        schemaType.addProperty(propertySeq);
        schemasBag.addProperty(schemaType);

        XMPSchema customSchema = xmp.createAndAddDefaultSchema(CUSTOM_NAMESPACE_PREFIX, CUSTOM_NAMESPACE_URI);

        int index = 1;
        for (Map.Entry<String, String> property : customProperties.entrySet()) {
            String localName = CUSTOM_PROPERTY_LOCAL_NAME_PREFIX + index++;

            PDFAPropertyType propertyType = new PDFAPropertyType(xmp);
            propertyType.addProperty(propertyType.createTextType(PDFAPropertyType.NAME, localName));
            propertyType.addProperty(new ChoiceType(xmp, propertyType.getNamespace(), propertyType.getPrefix(),
                    PDFAPropertyType.VALUETYPE, "Text"));
            propertyType.addProperty(new ChoiceType(xmp, propertyType.getNamespace(), propertyType.getPrefix(),
                    PDFAPropertyType.CATEGORY, "external"));
            propertyType.addProperty(propertyType.createTextType(PDFAPropertyType.DESCRIPTION, property.getKey()));
            propertySeq.addProperty(propertyType);

            customSchema.addProperty(customSchema.createTextType(localName, property.getValue()));

            if (DUPLICATE_CUSTOM_PROPERTIES_TO_INFO) {
                info.setCustomMetadataValue(property.getKey(), property.getValue());
            }
        }
    }

    private static Calendar toCalendar(LocalDate date) {
        Calendar calendar = new GregorianCalendar();
        calendar.clear();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        calendar.setTimeZone(java.util.TimeZone.getTimeZone(ZoneId.systemDefault()));
        return calendar;
    }
}
