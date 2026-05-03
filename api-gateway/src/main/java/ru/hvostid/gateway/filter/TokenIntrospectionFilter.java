package ru.hvostid.gateway.filter;

import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hvostid.common.contract.auth.IntrospectResponse;
import ru.hvostid.common.dto.ErrorResponse;
import ru.hvostid.common.http.SecurityHeaders;
import ru.hvostid.gateway.client.IntrospectionClient;
import ru.hvostid.gateway.config.AuthProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet filter that performs token introspection on every protected request.
 * <p>
 * For each non-public path the filter:
 * 1. Extracts the Bearer token from the Authorization header
 * 2. Calls Auth Service introspection endpoint via {@link IntrospectionClient}
 * 3. On success (active=true): injects {@link SecurityHeaders#USER_ID}
 * and {@link SecurityHeaders#USER_ROLES} into the request
 * 4. On failure (missing/invalid token or introspection error): returns 401
 * <p>
 * Public paths (login, register, actuator) bypass this filter entirely.
 * <p>
 * Ordered after {@link RequestIdFilter} so that Request ID is available in logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TokenIntrospectionFilter extends OncePerRequestFilter {
    static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(TokenIntrospectionFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final IntrospectionClient introspectionClient;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public TokenIntrospectionFilter(
            IntrospectionClient introspectionClient, AuthProperties authProperties, ObjectMapper objectMapper) {
        this.introspectionClient = introspectionClient;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return authProperties.publicPaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Missing or malformed Authorization header on {}", request.getRequestURI());
            writeUnauthorized(response, request.getRequestURI(), "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Optional<IntrospectResponse> result = introspectionClient.introspect(token);

        if (result.isEmpty() || !result.get().active()) {
            log.debug("Token introspection failed or inactive for {}", request.getRequestURI());
            writeUnauthorized(response, request.getRequestURI(), "Invalid or expired token");
            return;
        }

        IntrospectResponse introspection = result.get();
        String userId = String.valueOf(introspection.userId());
        String roles = String.join(",", introspection.roles());

        log.debug("Authenticated userId={} roles={} for {}", userId, roles, request.getRequestURI());

        HttpServletRequest wrappedRequest =
                new UserInfoHeaderWrapper(request, Map.of(USER_ID, userId, USER_ROLES, roles));
        filterChain.doFilter(wrappedRequest, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), message, path);

        objectMapper.writeValue(response.getWriter(), error);
    }

    /**
     * Request wrapper that injects identity headers
     * so downstream services receive authenticated user information.
     */
    static class UserInfoHeaderWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        UserInfoHeaderWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            for (var entry : extraHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            for (var entry : extraHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(entry.getValue()));
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            for (String key : extraHeaders.keySet()) {
                if (names.stream().noneMatch(key::equalsIgnoreCase)) {
                    names.add(key);
                }
            }
            return Collections.enumeration(names);
        }
    }
}
