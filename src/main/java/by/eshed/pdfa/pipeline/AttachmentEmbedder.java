package by.eshed.pdfa.pipeline;

import by.eshed.pdfa.model.SignatureAttachment;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Сводит отдельный файл подписи и документ "в один воспринимаемый документ" (описание задачи
 * 1.txt п.13) через embedded file PDF/A-3 (DECISIONS.md п.5: конвертация -> подпись, отдельный
 * файл подписи присоединяется вложением). Помимо Names/EmbeddedFiles пишется и каталоговый
 * массив /AF с /AFRelationship — это требование ISO 19005-3 (Associated Files) сверх PDF/A-2.
 */
public final class AttachmentEmbedder {

    private AttachmentEmbedder() {
    }

    public static void embed(PDDocument document, SignatureAttachment attachment) throws IOException {
        PDEmbeddedFile embeddedFile = new PDEmbeddedFile(document, new ByteArrayInputStream(attachment.data()));
        embeddedFile.setSubtype(attachment.mimeType());
        embeddedFile.setSize(attachment.data().length);

        PDComplexFileSpecification fileSpec = new PDComplexFileSpecification();
        fileSpec.setFile(attachment.fileName());
        fileSpec.setFileUnicode(attachment.fileName());
        fileSpec.setEmbeddedFile(embeddedFile);
        fileSpec.getCOSObject().setItem(COSName.AF_RELATIONSHIP, COSName.getPDFName("Source"));

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDDocumentNameDictionary names = catalog.getNames();
        if (names == null) {
            names = new PDDocumentNameDictionary(catalog);
            catalog.setNames(names);
        }
        PDEmbeddedFilesNameTreeNode embeddedFilesTree = names.getEmbeddedFiles();
        if (embeddedFilesTree == null) {
            embeddedFilesTree = new PDEmbeddedFilesNameTreeNode();
            names.setEmbeddedFiles(embeddedFilesTree);
        }
        Map<String, PDComplexFileSpecification> existing = embeddedFilesTree.getNames();
        Map<String, PDComplexFileSpecification> updated = existing == null ? new HashMap<>() : new HashMap<>(existing);
        updated.put(attachment.fileName(), fileSpec);
        embeddedFilesTree.setNames(updated);

        COSDictionary catalogDict = catalog.getCOSObject();
        COSBase existingAf = catalogDict.getDictionaryObject(COSName.AF);
        COSArray afArray = existingAf instanceof COSArray ? (COSArray) existingAf : new COSArray();
        afArray.add(fileSpec.getCOSObject());
        catalogDict.setItem(COSName.AF, afArray);
    }
}
