package com.certaaccess.evidencerfp;

import com.certaaccess.evidencerfp.analysis.api.AnalysisController;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.observability.CorrelationIdFilter;
import com.certaaccess.evidencerfp.observability.EvidenceRfpMetrics;
import com.certaaccess.evidencerfp.observability.PipelineMetricsAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EvidenceRfpApplicationSmokeTest {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void startsTheDemoApplicationWithApiJobsAndObservabilityWired() {
        assertThat(context.getBean(AnalysisController.class)).isNotNull();
        assertThat(context.getBean(AnalysisService.class)).isNotNull();
        assertThat(context.getBean(EvidenceRfpMetrics.class)).isNotNull();
        assertThat(context.getBean(PipelineMetricsAspect.class)).isNotNull();
        assertThat(context.getBean(CorrelationIdFilter.class)).isNotNull();
    }

    @Test
    void disablesTheHiddenSpringAiRetryLayer() {
        assertThat(environment.getProperty("spring.ai.retry.max-attempts", Integer.class))
                .isZero();
    }
}
