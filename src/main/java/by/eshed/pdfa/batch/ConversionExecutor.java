package by.eshed.pdfa.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Пул воркеров батч-конвертации с ограниченной очередью. Размер пула и
 * ёмкость очереди конфигурируются ({@code pdfa.batch.workers}/{@code pdfa.batch.queue-capacity});
 * переполнение очереди обрабатывается на уровне {@link BatchJobService} (проверка
 * {@link #remainingCapacity()} перед постановкой всех задач батча), а не через
 * {@code RejectedExecutionHandler} - это позволяет отклонить батч целиком одним
 * {@link BatchQueueFullException}, не поставив в очередь часть его документов.
 */
public final class ConversionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ConversionExecutor.class);

    private final ThreadPoolExecutor executor;

    /**
     * @param workers       число воркеров пула (core == max), не меньше 1
     * @param queueCapacity ёмкость ограниченной очереди задач, не меньше 1
     */
    public ConversionExecutor(int workers, int queueCapacity) {
        if (workers < 1) {
            throw new IllegalArgumentException("pdfa.batch.workers должно быть >= 1: " + workers);
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("pdfa.batch.queue-capacity должно быть >= 1: " + queueCapacity);
        }
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity));
        LOG.info("ConversionExecutor создан: workers={}, queueCapacity={}", workers, queueCapacity);
    }

    /** Свободные слоты в очереди задач прямо сейчас (без гарантии на момент фактической постановки). */
    public int remainingCapacity() {
        return executor.getQueue().remainingCapacity();
    }

    /** Ставит задачу в очередь без блокировки вызывающего потока. */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /**
     * Корректно останавливает пул (Spring вызывает через {@code @Bean(destroyMethod = "shutdown")}
     * при остановке контекста) - новые задачи не принимаются, уже поставленные в очередь
     * добегают до конца.
     */
    public void shutdown() {
        LOG.info("ConversionExecutor: остановка пула, в очереди {} задач", executor.getQueue().size());
        executor.shutdown();
    }
}
