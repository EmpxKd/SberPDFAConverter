package by.eshed.pdfa;

import by.eshed.pdfa.batch.BatchJobService;
import by.eshed.pdfa.batch.ConversionExecutor;
import by.eshed.pdfa.batch.ResultStore;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Точка входа Spring Boot: REST-сервис конвертации сканов в PDF/A, без CLI.
 */
@SpringBootApplication
@EnableScheduling
public class PdfaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfaApplication.class, args);
    }

    /**
     * Главный конвейер конвертации. {@code targetDpi}/{@code jpegQuality} настраиваются через
     * {@code pdfa.target-dpi}/{@code pdfa.jpeg-quality} (relaxed binding Spring Boot).
     */
    @Bean
    public ScanToPdfAConverter scanToPdfAConverter(@Value("${pdfa.target-dpi:300}") float targetDpi,
                                                    @Value("${pdfa.jpeg-quality:0.75}") float jpegQuality) {
        return new ScanToPdfAConverter(targetDpi, jpegQuality);
    }

    /**
     * Пул воркеров батч-конвертации. {@code destroyMethod = "shutdown"} - Spring сам останавливает
     * пул при остановке контекста, без отдельного {@code @PreDestroy}.
     */
    @Bean(destroyMethod = "shutdown")
    public ConversionExecutor conversionExecutor(@Value("${pdfa.batch.workers:0}") int workers,
                                                  @Value("${pdfa.batch.queue-capacity:1000}") int queueCapacity) {
        int effectiveWorkers = workers > 0 ? workers : Runtime.getRuntime().availableProcessors();
        return new ConversionExecutor(effectiveWorkers, queueCapacity);
    }

    /**
     * Хранилище готовых PDF батча на диске. Дефолт - подкаталог {@code pdfa-batch} во временном
     * каталоге JVM.
     */
    @Bean
    public ResultStore resultStore(@Value("${pdfa.batch.result-dir:}") String resultDir) {
        Path rootDir = (resultDir == null || resultDir.isBlank())
                ? Paths.get(System.getProperty("java.io.tmpdir"), "pdfa-batch")
                : Paths.get(resultDir);
        return new ResultStore(rootDir);
    }

    /** Реестр и оркестратор батч-заданий - связывает пул, хранилище и конвертер. */
    @Bean
    public BatchJobService batchJobService(ConversionExecutor conversionExecutor, ResultStore resultStore,
                                            ScanToPdfAConverter scanToPdfAConverter) {
        return new BatchJobService(conversionExecutor, resultStore, scanToPdfAConverter);
    }
}
