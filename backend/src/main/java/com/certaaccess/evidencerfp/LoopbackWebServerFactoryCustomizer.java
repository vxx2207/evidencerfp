package com.certaaccess.evidencerfp;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Erzwingt die lokale IPv4-Bindung auch bei höherrangigen externen Konfigurationswerten. */
@Component
final class LoopbackWebServerFactoryCustomizer
        implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>, Ordered {
    private static final InetAddress IPV4_LOOPBACK = ipv4Loopback();

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        factory.setAddress(IPV4_LOOPBACK);
    }

    /** Läuft nach dem Spring-Boot-Customizer, der {@code server.address} verarbeitet. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static InetAddress ipv4Loopback() {
        try {
            return InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        } catch (UnknownHostException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
