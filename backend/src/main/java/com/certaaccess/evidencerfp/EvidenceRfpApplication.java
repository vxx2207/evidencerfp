package com.certaaccess.evidencerfp;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EvidenceRfpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvidenceRfpApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI evidenceRfpOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EvidenceRFP API")
                .version("0.2.0")
                .description("Verifizierbare Antworten auf Security-Fragebögen mit IAM-Kontrollen. "
                        + "Die enthaltenen Demodaten sind vollständig synthetisch."));
    }
}
