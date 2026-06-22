package by.eshed.pdfa.ocr;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR-слой через Tesseract (tess4j, обёртка JNA над libtesseract — нативная зависимость,
 * допустимая по DECISIONS.md п.9). Каждый вызов создаёт свой экземпляр {@link Tesseract}: класс
 * не гарантирует потокобезопасность при повторном использовании одного инстанса из REST-сервера.
 */
public final class TesseractOcrEngine implements OcrEngine {

    private final String dataPath;
    private static volatile Boolean availableCache;

    public TesseractOcrEngine(String dataPath) {
        this.dataPath = dataPath;
    }

    @Override
    public List<RecognizedWord> recognizeWords(BufferedImage image, String language) {
        Tesseract tesseract = newTesseract(language);
        List<Word> words;
        try {
            words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        } catch (Throwable e) {
            throw new OcrUnavailableException("Tesseract OCR недоступен в окружении", e);
        }
        List<RecognizedWord> result = new ArrayList<>(words.size());
        for (Word word : words) {
            String text = word.getText() == null ? "" : word.getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            java.awt.Rectangle box = word.getBoundingBox();
            result.add(new RecognizedWord(text, box.x, box.y, box.width, box.height, word.getConfidence()));
        }
        return result;
    }

    @Override
    public boolean isAvailable() {
        Boolean cached = availableCache;
        if (cached != null) {
            return cached;
        }
        boolean available;
        try {
            BufferedImage probe = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
            newTesseract("eng").getWords(probe, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            available = true;
        } catch (Throwable e) {
            available = false;
        }
        availableCache = available;
        return available;
    }

    private Tesseract newTesseract(String language) {
        Tesseract tesseract = new Tesseract();
        if (dataPath != null && !dataPath.isBlank()) {
            tesseract.setDatapath(dataPath);
        }
        tesseract.setLanguage(language);
        return tesseract;
    }
}
