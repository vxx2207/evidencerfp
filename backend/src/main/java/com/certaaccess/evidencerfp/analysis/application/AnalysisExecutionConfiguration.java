package com.certaaccess.evidencerfp.analysis.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AnalysisExecutionConfiguration {

    @Bean("analysisTaskExecutor")
    TaskExecutor analysisTaskExecutor(
            @Value("${evidence-rfp.analysis.workers:2}") int workers,
            @Value("${evidence-rfp.analysis.queue-capacity:4}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, workers));
        executor.setMaxPoolSize(Math.max(1, workers));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("evidence-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
