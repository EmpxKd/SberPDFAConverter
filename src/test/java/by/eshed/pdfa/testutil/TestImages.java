package by.eshed.pdfa.testutil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/** Генерирует тестовые изображения в памяти, чтобы тесты не зависели от внешних файлов-фикстур. */
public final class TestImages {

    private TestImages() {
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
