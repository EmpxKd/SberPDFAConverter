package by.eshed.pdfa.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * TTL-уборка результатов батча: диск под результаты не бесконечен, поэтому
 * каталоги завершённых заданий старше {@code pdfa.batch.result-ttl-hours} периодически удаляются
 * вместе с записью в реестре {@link BatchJobService}.
 */
@Component
public class ResultCleanupTask {

    private static final Logger LOG = LoggerFactory.getLogger(ResultCleanupTask.class);

    private final BatchJobService batchJobService;
    private final ResultStore resultStore;
    private final long ttlHours;

    /**
     * @param ttlHours сколько часов с момента регистрации батча хранить результаты на диске
     */
    public ResultCleanupTask(BatchJobService batchJobService, ResultStore resultStore,
                              @Value("${pdfa.batch.result-ttl-hours:24}") long ttlHours) {
        this.batchJobService = batchJobService;
        this.resultStore = resultStore;
        this.ttlHours = ttlHours;
    }

    /**
     * Раз в час сканирует реестр заданий и удаляет батчи старше TTL. Период проверки
     * (фиксированный 1 час) не выносится в конфиг отдельно - агрессивность уборки регулируется
     * самим {@code pdfa.batch.result-ttl-hours}.
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanup() {
        Instant threshold = Instant.now().minus(Duration.ofHours(ttlHours));
        int removed = 0;
        for (BatchJob job : batchJobService.allJobs()) {
            if (!job.createdAt().isBefore(threshold)) {
                continue;
            }
            try {
                resultStore.deleteJob(job.jobId());
            } catch (IOException e) {
                LOG.warn("Не удалось удалить каталог батча по TTL: jobId={}", job.jobId(), e);
                continue;
            }
            batchJobService.remove(job.jobId());
            removed++;
        }
        if (removed > 0) {
            LOG.info("TTL-уборка батчей: удалено {} заданий старше {}ч", removed, ttlHours);
        }
    }
}
