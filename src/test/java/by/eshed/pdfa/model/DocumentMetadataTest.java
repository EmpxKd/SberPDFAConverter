package by.eshed.pdfa.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PLAN.md, задача A1: открытый словарь {@code customProperties} — не должен конфликтовать со
 * стандартными полями docinfo/XMP и должен сохранять порядок вставки заказчика.
 */
class DocumentMetadataTest {

    @ParameterizedTest
    @ValueSource(strings = {"title", "Author", "SUBJECT", "Keywords", "creator", "Producer",
            "creationDate", "ModDate"})
    void rejectsReservedKeysCaseInsensitively(String reservedKey) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DocumentMetadata.builder().customProperty(reservedKey, "значение"));
        assertTrue(ex.getMessage().contains(reservedKey));
    }

    @Test
    void skipsBlankOrNullKeyOrValueSilently() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .customProperty(null, "значение")
                .customProperty("  ", "значение")
                .customProperty("Ключ", null)
                .customProperty("Ключ2", "  ")
                .build();
        assertTrue(metadata.customProperties().isEmpty());
    }

    @Test
    void defaultsToEmptyNeverNull() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();
        assertEquals(Map.of(), metadata.customProperties());
    }

    @Test
    void preservesInsertionOrderAcrossCustomPropertyCalls() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .customProperty("Отдел", "Бухгалтерия")
                .customProperty("НомерДела", "12345")
                .customProperty("Архив", "Да")
                .build();

        assertEquals(List.of("Отдел", "НомерДела", "Архив"),
                List.copyOf(metadata.customProperties().keySet()));
    }

    @Test
    void preservesInsertionOrderFromBulkMap() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Зет", "1");
        source.put("Альфа", "2");
        source.put("Мю", "3");

        DocumentMetadata metadata = DocumentMetadata.builder().customProperties(source).build();

        assertEquals(List.of("Зет", "Альфа", "Мю"), List.copyOf(metadata.customProperties().keySet()));
    }

    @Test
    void reAddingSameKeyUpdatesValueButKeepsOriginalPosition() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .customProperty("Первый", "a")
                .customProperty("Второй", "b")
                .customProperty("Первый", "обновлено")
                .build();

        assertEquals(List.of("Первый", "Второй"), List.copyOf(metadata.customProperties().keySet()));
        assertEquals("обновлено", metadata.customProperties().get("Первый"));
    }

    @Test
    void customPropertiesMapIsUnmodifiable() {
        DocumentMetadata metadata = DocumentMetadata.builder().customProperty("Ключ", "Значение").build();
        assertThrows(UnsupportedOperationException.class, () -> metadata.customProperties().put("x", "y"));
    }

    @Test
    void bulkCustomPropertiesNullIsNoOp() {
        DocumentMetadata metadata = DocumentMetadata.builder().customProperties(null).build();
        assertTrue(metadata.customProperties().isEmpty());
    }
}
