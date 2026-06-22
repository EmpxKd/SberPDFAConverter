package by.eshed.pdfa.image;

import by.eshed.pdfa.model.PageSource;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Декодирует TIFF (в т.ч. многостраничный)/JPEG/PNG/BMP через штатный javax.imageio (см.
 * IMPLEMENTATION_LOG.md — JDK 9+ включает встроенный TIFF-плагин, внешняя библиотека не нужна)
 * и приводит страницы к единому DPI (DECISIONS.md п.3 и п.8).
 */
public final class ImageNormalizer {

    private final float targetDpi;

    public ImageNormalizer(float targetDpi) {
        this.targetDpi = targetDpi;
    }

    public List<NormalizedPage> readPages(PageSource source) throws IOException {
        String formatName;
        switch (source.format()) {
            case TIFF:
                formatName = "tiff";
                break;
            case JPEG:
                formatName = "JPEG";
                break;
            case PNG:
                formatName = "png";
                break;
            case BMP:
                formatName = "bmp";
                break;
            default:
                throw new IllegalArgumentException("ImageNormalizer не читает " + source.format());
        }

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
        if (!readers.hasNext()) {
            throw new IOException("Нет ImageIO-ридера для формата " + formatName);
        }
        ImageReader reader = readers.next();
        List<NormalizedPage> pages = new ArrayList<>();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source.data()))) {
            reader.setInput(iis, false, false);
            int numImages = reader.getNumImages(true);
            for (int i = 0; i < numImages; i++) {
                BufferedImage raw = reader.read(i);
                float dpi = extractDpi(safeMetadata(reader, i)).orElse(targetDpi);
                boolean bilevel = isBilevel(raw);
                BufferedImage normalized = resampleIfNeeded(raw, dpi, bilevel);
                pages.add(new NormalizedPage(normalized, targetDpi, bilevel));
            }
        } finally {
            reader.dispose();
        }
        if (pages.isEmpty()) {
            throw new IOException("Файл не содержит изображений");
        }
        return pages;
    }

    private IIOMetadata safeMetadata(ImageReader reader, int index) {
        try {
            return reader.getImageMetadata(index);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isBilevel(BufferedImage image) {
        return image.getColorModel().getPixelSize() == 1;
    }

    private Optional<Float> extractDpi(IIOMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        for (String formatName : metadata.getMetadataFormatNames()) {
            if (!"javax_imageio_1.0".equals(formatName)) {
                continue;
            }
            Node root = metadata.getAsTree(formatName);
            Node dimension = findChild(root, "Dimension");
            if (dimension == null) {
                continue;
            }
            Node horizontal = findChild(dimension, "HorizontalPixelSize");
            String mmPerPixel = horizontal == null ? null : attr(horizontal, "value");
            if (mmPerPixel == null) {
                continue;
            }
            try {
                double mm = Double.parseDouble(mmPerPixel);
                if (mm > 0) {
                    return Optional.of((float) (25.4 / mm));
                }
            } catch (NumberFormatException ignored) {
                // нечитаемое значение метаданных - используем дефолтный DPI
            }
        }
        return Optional.empty();
    }

    private Node findChild(Node parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private String attr(Node node, String attrName) {
        if (node.getAttributes() == null) {
            return null;
        }
        Node attr = node.getAttributes().getNamedItem(attrName);
        return attr == null ? null : attr.getNodeValue();
    }

    private BufferedImage resampleIfNeeded(BufferedImage raw, float sourceDpi, boolean bilevel) {
        if (Math.abs(sourceDpi - targetDpi) / targetDpi < 0.02f) {
            return raw; // отклонение в пределах 2% - нормализация не требуется
        }
        float scale = targetDpi / sourceDpi;
        int newWidth = Math.max(1, Math.round(raw.getWidth() * scale));
        int newHeight = Math.max(1, Math.round(raw.getHeight() * scale));
        return bilevel ? resampleBilevel(raw, newWidth, newHeight) : resampleColor(raw, newWidth, newHeight);
    }

    private BufferedImage resampleColor(BufferedImage raw, int newWidth, int newHeight) {
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(raw, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    private BufferedImage resampleBilevel(BufferedImage raw, int newWidth, int newHeight) {
        // Прямое уменьшение 1-битного изображения даёт грубый алиасинг: масштабируем через
        // промежуточный градационный буфер, обратное приведение к 1 биту делает Java2D
        // при отрисовке на двухцветный ColorModel (ближайшее совпадение цвета).
        BufferedImage gray = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(raw, 0, 0, newWidth, newHeight, null);
        g.dispose();

        BufferedImage binary = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D gb = binary.createGraphics();
        gb.drawImage(gray, 0, 0, null);
        gb.dispose();
        return binary;
    }
}
