package by.eshed.pdfa.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Строит минимальное дерево структуры (Tagged PDF), нужное только для PDF/A-1a
 * (ISO 19005-1, 6.8: {@code /MarkInfo}, {@code /StructTreeRoot}). Дерево строится содержательно
 * (Document -> Figure на страницу, привязанный к реальному marked-content и ParentTree), но без
 * избыточной детализации (без Span/ActualText на словах, без RoleMap) — veraPDF не требует
 * большего для прохождения правил clause="6.8.*".
 */
public final class StructureTreeBuilder {

    /** Каждая страница оборачивается в ровно один marked-content диапазон /Figure с этим MCID. */
    public static final int FIGURE_MCID = 0;

    private static final Logger LOG = Logger.getLogger(StructureTreeBuilder.class.getName());

    private final PDStructureTreeRoot structureTreeRoot = new PDStructureTreeRoot();
    private final PDStructureElement documentElement =
            new PDStructureElement(StandardStructureTypes.DOCUMENT, structureTreeRoot);
    private final Map<Integer, COSObjectable> parentTreeEntries = new LinkedHashMap<>();
    private int pageCount;

    /**
     * Создаёт корень структуры с единственным узлом верхнего уровня {@code /Document}.
     */
    public StructureTreeBuilder() {
        structureTreeRoot.appendKid(documentElement);
    }

    /**
     * Регистрирует страницу как структурный элемент {@code /Figure} (скан-изображение) и
     * связывает её с будущим marked-content диапазоном через {@code /StructParents}.
     * Должно быть вызвано один раз на страницу, до записи содержимого {@code drawImage}.
     *
     * @param page    страница PDF, на которой будет нарисовано изображение
     * @param altText альтернативное текстовое описание ({@code /Alt}) для нетекстового контента
     * @return MCID, который вызывающий код должен передать в
     *         {@link org.apache.pdfbox.pdmodel.PDPageContentStream#beginMarkedContent}
     *         при рисовании изображения этой страницы (всегда {@link #FIGURE_MCID})
     */
    public int registerFigurePage(PDPage page, String altText) {
        if (page == null) {
            throw new IllegalArgumentException("page не может быть null");
        }
        int structParents = pageCount++;
        PDStructureElement figure = new PDStructureElement(StandardStructureTypes.Figure, documentElement);
        figure.setPage(page);
        if (altText != null && !altText.isBlank()) {
            figure.setAlternateDescription(altText);
        }
        figure.appendKid(FIGURE_MCID);
        documentElement.appendKid(figure);
        page.setStructParents(structParents);
        parentTreeEntries.put(structParents, figure);
        LOG.fine(() -> "Зарегистрирован структурный элемент Figure для страницы #" + structParents);
        return FIGURE_MCID;
    }

    /**
     * Завершает построение: записывает {@code /MarkInfo}, {@code /StructTreeRoot} (с
     * ParentTree) и, если указан, {@code /Lang} в каталог документа. Вызывается один раз
     * после того, как все страницы добавлены через {@link #registerFigurePage}.
     *
     * @param document документ, в каталог которого записывается структура
     * @param language язык документа (BCP-47/ISO 639) для {@code /Lang}; {@code null} - не писать
     */
    public void applyTo(PDDocument document, String language) {
        if (document == null) {
            throw new IllegalArgumentException("document не может быть null");
        }
        PDMarkInfo markInfo = new PDMarkInfo();
        markInfo.setMarked(true);

        PDNumberTreeNode parentTree = new PDNumberTreeNode(PDStructureElement.class);
        parentTree.setNumbers(parentTreeEntries);
        structureTreeRoot.setParentTree(parentTree);
        structureTreeRoot.setParentTreeNextKey(pageCount);

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setMarkInfo(markInfo);
        catalog.setStructureTreeRoot(structureTreeRoot);
        if (language != null && !language.isBlank()) {
            catalog.setLanguage(language);
        }
        LOG.info(() -> "Дерево структуры PDF/A-1a применено: страниц=" + pageCount);
    }
}
