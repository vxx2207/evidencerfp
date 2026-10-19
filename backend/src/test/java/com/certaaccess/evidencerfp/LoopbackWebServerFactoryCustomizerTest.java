package com.certaaccess.evidencerfp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.core.Ordered;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackWebServerFactoryCustomizerTest {

    @Test
    void overridesExternallyConfiguredWildcardAddressAfterBootConfiguration() throws Exception {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setAddress(InetAddress.getByName("0.0.0.0"));
        LoopbackWebServerFactoryCustomizer customizer = new LoopbackWebServerFactoryCustomizer();

        customizer.customize(factory);

        assertThat(factory.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(customizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
