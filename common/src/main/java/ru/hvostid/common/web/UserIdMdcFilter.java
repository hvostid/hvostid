package ru.hvostid.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hvostid.common.http.SecurityHeaders;

/**
 * Pushes the gateway-provided {@link SecurityHeaders#USER_ID} header into SLF4J MDC under
 * {@code userId} so structured log lines can be filtered by user. Runs after
 * {@link RequestIdMdcFilter} so a single request's log lines carry both the request id and
 * the user id once the gateway has authenticated the caller.
 *
 * <p>Anonymous requests (no header) leave the slot empty; the JSON encoder omits the field.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UserIdMdcFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(SecurityHeaders.USER_ID);
        boolean set = false;
        if (userId != null && !userId.isBlank()) {
            MDC.put(MDC_KEY, userId);
            set = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (set) {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
