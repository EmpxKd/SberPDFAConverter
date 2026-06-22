package by.eshed.pdfa.image;

import java.awt.image.BufferedImage;

/**
 * Страница скана после нормализации: приведена к известному DPI и классифицирована по
 * цветности, чтобы конвертер выбрал способ сжатия (DECISIONS.md п.8: ч/б — CCITT G4,
 * цвет/градации серого — JPEG).
 */
public final class NormalizedPage {

    private final BufferedImage image;
    private final float dpi;
    private final boolean bilevel;

    public NormalizedPage(BufferedImage image, float dpi, boolean bilevel) {
        this.image = image;
        this.dpi = dpi;
        this.bilevel = bilevel;
    }

    public BufferedImage image() {
        return image;
    }

    public float dpi() {
        return dpi;
    }

    public boolean isBilevel() {
        return bilevel;
    }
}
