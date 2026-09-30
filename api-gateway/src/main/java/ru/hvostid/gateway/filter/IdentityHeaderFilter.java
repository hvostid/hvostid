package ru.hvostid.gateway.filter;

import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Removes untrusted identity before any public-path or authentication decision. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdentityHeaderFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    private boolean identity(String name) {
                        return USER_ID.equalsIgnoreCase(name) || USER_ROLES.equalsIgnoreCase(name);
                    }

                    @Override
                    public String getHeader(String name) {
                        return identity(name) ? null : super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        return identity(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
                    }

                    @Override
                    public Enumeration<String> getHeaderNames() {
                        return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                                .filter(name -> !identity(name))
                                .toList());
                    }
                },
                response);
    }
}
