package ru.hvostid.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hvostid.common.http.SecurityHeaders;

/**
 * Backend servlet filter that mirrors the api-gateway's request-id behavior inside each
 * downstream service: read {@link SecurityHeaders#REQUEST_ID} from the incoming request
 * (generating a new UUID if absent), push it into SLF4J MDC under {@code requestId}, and
 * mirror the value back as a response header. The error handler picks the same value out
 * of MDC for the {@code traceId} field on {@link ru.hvostid.common.dto.ProblemDetails}.
 *
 * <p>Registered automatically via {@code HvostidErrorAutoConfiguration} so each service
 * gets it without explicit bean wiring.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdMdcFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(SecurityHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(SecurityHeaders.REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
