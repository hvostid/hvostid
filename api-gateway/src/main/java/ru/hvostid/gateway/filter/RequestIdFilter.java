package ru.hvostid.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Global servlet filter that ensures every request has an X-Request-Id header.
 * <p>
 * If the incoming request already contains X-Request-Id, its value is reused.
 * Otherwise, a new UUID is generated. The header is:
 * - forwarded to downstream services (via request wrapper)
 * - added to the response so the client can see it
 * - placed into SLF4J MDC as "requestId" for structured logging
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        boolean generated = false;

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            generated = true;
        }

        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        try {
            log.debug("Request {} {} requestId={} (generated={})", request.getMethod(), request.getRequestURI(), requestId, generated);

            // Wrap the request so the gateway forwards X-Request-Id to downstream services
            HttpServletRequest wrappedRequest = generated ? new RequestIdHeaderWrapper(request, requestId) : request;

            // Add X-Request-Id to the response for the client
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(wrappedRequest, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    /**
     * Wrapper that injects the X-Request-Id header into the request
     * when the client did not provide one.
     */
    static class RequestIdHeaderWrapper extends HttpServletRequestWrapper {
        private final String requestId;

        RequestIdHeaderWrapper(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(requestId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(REQUEST_ID_HEADER::equalsIgnoreCase)) {
                names.add(REQUEST_ID_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
