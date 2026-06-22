package by.eshed.pdfa.testutil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/** Генерирует тестовые изображения в памяти, чтобы тесты не зависели от внешних файлов-фикстур. */
public final class TestImages {

    /** Реальный 4-компонентный CMYK JPEG (Adobe APP14, transform=0) — javax.imageio не умеет писать
     * такой формат программно, поэтому это единственный бинарный файл-фикстура в проекте. */
    private static final String CMYK_JPEG_RESOURCE = "/samples/cmyk_sample.jpg";

    private TestImages() {
    }

    public static byte[] cmykJpegSample() throws IOException {
        try (InputStream in = TestImages.class.getResourceAsStream(CMYK_JPEG_RESOURCE)) {
            if (in == null) {
                throw new IOException("Тестовая фикстура не найдена в classpath: " + CMYK_JPEG_RESOURCE);
            }
            return in.readAllBytes();
        }
    }

    public static BufferedImage grayscalePage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.GRAY);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        return image;
    }

    /** Полупрозрачный круг на прозрачном фоне — PDF/A-1 запрещает soft mask/прозрачность (PDFA1_REQUIREMENTS.md),
     * поэтому при конвертации альфа-канал должен быть сведён на непрозрачный (белый) фон. */
    public static BufferedImage colorPageWithAlpha(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(255, 0, 0, 180));
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        return image;
    }

    public static byte[] encodeUncompressedBmp(BufferedImage image) throws IOException {
        return encode(image, "bmp");
    }

    /** Однокадровый TIFF с явным XResolution/YResolution (TIFF rational, ResolutionUnit=2=inch) —
     * проверено round-trip-ом через javax_imageio_1.0/Dimension/HorizontalPixelSize, в отличие от
     * PNG/JPEG-плагинов JDK, которые это поле при записи игнорируют. */
    public static byte[] encodeTiffWithDpi(BufferedImage image, float dpi) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            ImageTypeSpecifier typeSpecifier = new ImageTypeSpecifier(image);
            IIOMetadata defaultMetadata = writer.getDefaultImageMetadata(typeSpecifier, param);

            TIFFDirectory directory = TIFFDirectory.createFromMetadata(defaultMetadata);
            BaselineTIFFTagSet baseline = BaselineTIFFTagSet.getInstance();
            long[][] rational = {{(long) dpi, 1}};
            directory.addTIFFField(new TIFFField(
                    baseline.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1, rational));
            directory.addTIFFField(new TIFFField(
                    baseline.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1, rational));
            directory.addTIFFField(new TIFFField(
                    baseline.getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT), TIFFTag.TIFF_SHORT, 1, new char[]{2}));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, directory.getAsMetadata()), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    public static BufferedImage bilevelPage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, width - 20, height - 20);
        g.dispose();
        return image;
    }

    public static BufferedImage colorPage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillOval(20, 20, width / 3, height / 3);
        g.dispose();
        return image;
    }

    public static byte[] encode(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, formatName, out)) {
            throw new IOException("Нет writer для формата " + formatName);
        }
        return out.toByteArray();
    }

    public static byte[] encodeMultiPageTiff(List<BufferedImage> pages) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (BufferedImage page : pages) {
                writer.writeInsert(-1, new IIOImage(page, null, null), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
