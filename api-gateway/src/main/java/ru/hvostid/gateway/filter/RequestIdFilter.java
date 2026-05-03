package ru.hvostid.gateway.filter;

import static ru.hvostid.common.http.SecurityHeaders.REQUEST_ID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hvostid.common.http.SecurityHeaders;

/**
 * Global servlet filter that ensures every request has {@link SecurityHeaders#REQUEST_ID}.
 * <p>
 * If the incoming request already contains {@link SecurityHeaders#REQUEST_ID}, its value is reused.
 * Otherwise, a new UUID is generated. The value is:
 * - forwarded to downstream services (via request wrapper)
 * - added to the response so the client can see it
 * - placed into SLF4J MDC as "requestId" for structured logging
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID);
        boolean generated = false;

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
            generated = true;
        }

        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        try {
            log.debug(
                    "Request {} {} requestId={} (generated={})",
                    request.getMethod(),
                    request.getRequestURI(),
                    requestId,
                    generated);

            // Wrap the request so the gateway forwards REQUEST_ID to downstream services.
            HttpServletRequest wrappedRequest = generated ? new RequestIdHeaderWrapper(request, requestId) : request;

            // Add REQUEST_ID to the response for the client.
            response.setHeader(REQUEST_ID, requestId);

            filterChain.doFilter(wrappedRequest, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    /**
     * Wrapper that injects {@link SecurityHeaders#REQUEST_ID} into the request
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
            if (REQUEST_ID.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (REQUEST_ID.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(requestId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(REQUEST_ID::equalsIgnoreCase)) {
                names.add(REQUEST_ID);
            }
            return Collections.enumeration(names);
        }
    }
}
