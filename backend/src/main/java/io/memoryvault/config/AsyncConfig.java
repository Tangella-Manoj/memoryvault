package io.memoryvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * A real production bug traced back to this bean's original config
     * (corePoolSize=4, maxPoolSize=8, queueCapacity=200 — 208 total capacity, with the
     * default {@code AbortPolicy}): a 500-item bulk import burst submitted enrichment
     * tasks faster than they could drain, exceeded that 208-task capacity, and every
     * submission past it threw {@code RejectedExecutionException}. That exception was
     * thrown from inside Spring's transaction-synchronization {@code afterCommit}
     * dispatch (the {@code @TransactionalEventListener} async hop), which Spring's
     * {@code AbstractPlatformTransactionManager} deliberately catches and only logs —
     * it never reaches application code. The result: ~40% of a large batch silently
     * stuck in {@code PROCESSING} forever, no exception anywhere in application logs,
     * job status still reporting success. Two changes prevent this: a much larger queue
     * (comfortably covers bursts far beyond the current 500-URL import cap), and
     * {@code CallerRunsPolicy} as a backstop — if capacity is ever exceeded regardless,
     * the submitting thread runs the task itself (throttling, not silent data loss)
     * instead of the task being discarded.
     */
    @Bean(name = "intelligenceExecutor")
    public Executor intelligenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("intelligence-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
