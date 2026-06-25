package by.eshed.pdfa.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Хранилище готовых PDF батча на диске - тысячи готовых PDF в памяти процесса означали бы OOM,
 * поэтому каждый документ пишется на диск сразу после конвертации и читается с диска при
 * скачивании. Структура каталога: {@code <result-dir>/<jobId>/<safeDocId>.pdf}.
 */
public final class ResultStore {

    private static final Logger LOG = LoggerFactory.getLogger(ResultStore.class);
    private static final String PDF_SUFFIX = ".pdf";

    private final Path rootDir;

    /**
     * @param rootDir корневой каталог результатов ({@code pdfa.batch.result-dir}); создаётся,
     *                если ещё не существует
     */
    public ResultStore(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать каталог результатов батча: " + rootDir, e);
        }
        LOG.info("ResultStore: каталог результатов {}", rootDir.toAbsolutePath());
    }

    /**
     * Сохраняет готовый PDF документа на диск.
     *
     * @param jobId  идентификатор батча
     * @param docId  исходный (не санитизированный) ключ документа
     * @param bytes  готовый PDF
     * @throws IOException при ошибке записи на диск
     */
    public void store(String jobId, String docId, byte[] bytes) throws IOException {
        Path jobDir = jobDir(jobId);
        Files.createDirectories(jobDir);
        Path target = jobDir.resolve(safeFileName(docId));
        Files.write(target, bytes);
    }

    /**
     * @return путь к PDF документа, если он существует на диске, иначе {@code Optional#empty()}
     */
    public Optional<Path> resolve(String jobId, String docId) {
        Path candidate = jobDir(jobId).resolve(safeFileName(docId));
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    /**
     * Удаляет каталог батча целиком (после TTL или сразу после скачивания, если
     * {@code pdfa.batch.delete-on-download=true}). Не бросает исключение, если каталога уже нет.
     */
    public void deleteJob(String jobId) throws IOException {
        Path dir = jobDir(jobId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
        LOG.info("ResultStore: каталог батча удалён {}", dir);
    }

    private Path jobDir(String jobId) {
        return rootDir.resolve(safeFileName(jobId));
    }

    /**
     * Безопасное имя файла/каталога для {@code jobId}/{@code docId}: убирает {@code / \ ..} и
     * управляющие символы (защита от path traversal/zip-slip) - кириллица и пробелы внутри имени
     * остаются.
     *
     * @param rawId исходный, не санитизированный идентификатор
     * @return безопасное имя файла без расширения {@code .pdf}; для записи/чтения PDF к нему
     *         добавляется {@value #PDF_SUFFIX}
     */
    public static String safeName(String rawId) {
        String sanitized = rawId
                .replace("..", "_")
                .replaceAll("[\\\\/\\x00-\\x1f]", "_")
                .trim();
        if (sanitized.isEmpty()) {
            sanitized = "_";
        }
        return sanitized;
    }

    private static String safeFileName(String rawId) {
        return safeName(rawId) + PDF_SUFFIX;
    }

    /** Удобный фабричный метод из строкового пути конфигурации ({@code pdfa.batch.result-dir}). */
    public static ResultStore atPath(String path) {
        return new ResultStore(Paths.get(path));
    }
}
