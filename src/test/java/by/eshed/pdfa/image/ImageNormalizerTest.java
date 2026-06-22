package by.eshed.pdfa.image;

import by.eshed.pdfa.model.PageSource;
import by.eshed.pdfa.model.SourceFormat;
import by.eshed.pdfa.testutil.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageNormalizerTest {

    @Test
    void readsSinglePageJpegAsColor() throws Exception {
        byte[] jpeg = TestImages.encode(TestImages.colorPage(200, 150), "JPEG");
        List<NormalizedPage> pages = new ImageNormalizer(300f).readPages(new PageSource(jpeg, SourceFormat.JPEG));

        assertEquals(1, pages.size());
        assertFalse(pages.get(0).isBilevel());
    }

    @Test
    void readsSinglePagePngAsBilevel() throws Exception {
        byte[] png = TestImages.encode(TestImages.bilevelPage(200, 150), "png");
        List<NormalizedPage> pages = new ImageNormalizer(300f).readPages(new PageSource(png, SourceFormat.PNG));

        assertEquals(1, pages.size());
        assertTrue(pages.get(0).isBilevel());
    }

    @Test
    void readsMultiPageTiffAsMultiplePages() throws Exception {
        BufferedImage page1 = TestImages.bilevelPage(100, 80);
        BufferedImage page2 = TestImages.bilevelPage(100, 80);
        byte[] tiff = TestImages.encodeMultiPageTiff(List.of(page1, page2));

        List<NormalizedPage> pages = new ImageNormalizer(300f).readPages(new PageSource(tiff, SourceFormat.TIFF));

        assertEquals(2, pages.size());
    }
}
