package com.certaaccess.evidencerfp.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && supplied.matches("[A-Za-z0-9._-]{8,80}")
                ? supplied : UUID.randomUUID().toString();
        response.setHeader(HEADER, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
