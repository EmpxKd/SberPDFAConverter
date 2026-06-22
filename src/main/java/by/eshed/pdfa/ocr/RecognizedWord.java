package by.eshed.pdfa.ocr;

/**
 * Слово, распознанное OCR, с ограничивающим прямоугольником в пиксельных координатах
 * исходного изображения страницы (origin — верхний левый угол, как у BufferedImage).
 */
public final class RecognizedWord {

    private final String text;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float confidence;

    public RecognizedWord(String text, int x, int y, int width, int height, float confidence) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.confidence = confidence;
    }

    public String text() {
        return text;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float confidence() {
        return confidence;
    }
}
