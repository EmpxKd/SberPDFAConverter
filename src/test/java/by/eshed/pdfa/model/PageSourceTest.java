package by.eshed.pdfa.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Граница ввода одного файла скана: null и пустые данные должны давать понятное исключение. */
class PageSourceTest {

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new PageSource(null, SourceFormat.PNG));
    }

    @Test
    void rejectsNullFormat() {
        assertThrows(NullPointerException.class, () -> new PageSource(new byte[]{1}, null));
    }

    @Test
    void rejectsEmptyData() {
        assertThrows(IllegalArgumentException.class, () -> new PageSource(new byte[0], SourceFormat.PNG));
    }
}