package by.eshed.pdfa.ocr;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Абстракция движка OCR — конвейер (ScanToPdfAConverter) зависит только от этого интерфейса,
 * чтобы конкретную реализацию (Tesseract/tess4j) можно было подменить тестовым стабом, когда
 * в окружении не установлен сам Tesseract (нативная зависимость, см. DECISIONS.md п.9).
 */
public interface OcrEngine {

    List<RecognizedWord> recognizeWords(BufferedImage image, String language);

    boolean isAvailable();
}
