package com.certaaccess.evidencerfp.analysis.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class AnalysisExecutionConfiguration {

    @Bean("analysisTaskExecutor")
    ThreadPoolTaskExecutor analysisTaskExecutor(
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

    @Bean("analysisHeartbeatScheduler")
    TaskScheduler analysisHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("evidence-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
