package by.eshed.pdfa.metadata;

import by.eshed.pdfa.model.DocumentMetadata;
import by.eshed.pdfa.model.PdfAFlavourOption;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MetadataMapper — единственное место, которое пишет docinfo и XMP, всегда синхронно
 * (ISO 19005 Info-dict <-> XMP). pdfaid:part/conformance должны в точности соответствовать
 * {@link PdfAFlavourOption}, который передаётся конвертером (PLAN.md, задача 1).
 */
class MetadataMapperTest {

    @Test
    void writesPdfaIdentificationMatchingFlavour1b() throws Exception {
        XMPMetadata xmp = applyAndReadXmp(DocumentMetadata.builder().build(), PdfAFlavourOption.PDF_A_1B);

        PDFAIdentificationSchema idSchema = xmp.getPDFAIdentificationSchema();
        assertNotNull(idSchema, "XMP должен содержать схему pdfaid");
        assertEquals(1, idSchema.getPart().intValue());
        assertEquals("B", idSchema.getConformance());
    }

    @Test
    void writesPdfaIdentificationMatchingFlavour1a() throws Exception {
        XMPMetadata xmp = applyAndReadXmp(DocumentMetadata.builder().build(), PdfAFlavourOption.PDF_A_1A);

        PDFAIdentificationSchema idSchema = xmp.getPDFAIdentificationSchema();
        assertEquals(1, idSchema.getPart().intValue());
        assertEquals("A", idSchema.getConformance());
    }

    @Test
    void syncsTitleAuthorSubjectBetweenInfoAndXmp() throws Exception {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .title("Дело №1")
                .author("Тестировщик")
                .subject("Акт приёмки")
                .documentDate(LocalDate.of(2024, 5, 1))
                .build();

        try (PDDocument document = new PDDocument()) {
            MetadataMapper.apply(document, metadata, PdfAFlavourOption.PDF_A_1B);

            assertEquals("Дело №1", document.getDocumentInformation().getTitle());
            assertEquals("Тестировщик", document.getDocumentInformation().getAuthor());
            assertEquals("Акт приёмки", document.getDocumentInformation().getSubject());

            XMPMetadata xmp = readXmp(document);
            assertEquals("Дело №1", xmp.getDublinCoreSchema().getTitle());
            assertEquals("Тестировщик", xmp.getDublinCoreSchema().getCreators().get(0));
        }
    }

    @Test
    void omitsUnsetFieldsFromBothInfoAndXmp() throws Exception {
        try (PDDocument document = new PDDocument()) {
            MetadataMapper.apply(document, DocumentMetadata.builder().build(), PdfAFlavourOption.PDF_A_1B);

            assertNull(document.getDocumentInformation().getTitle());
            XMPMetadata xmp = readXmp(document);
            assertNull(xmp.getDublinCoreSchema());
        }
    }

    private static XMPMetadata applyAndReadXmp(DocumentMetadata metadata, PdfAFlavourOption flavour) throws Exception {
        try (PDDocument document = new PDDocument()) {
            MetadataMapper.apply(document, metadata, flavour);
            return readXmp(document);
        }
    }

    private static XMPMetadata readXmp(PDDocument document) throws Exception {
        try (var xmpStream = document.getDocumentCatalog().getMetadata().exportXMPMetadata()) {
            return new DomXmpParser().parse(xmpStream);
        }
    }
}