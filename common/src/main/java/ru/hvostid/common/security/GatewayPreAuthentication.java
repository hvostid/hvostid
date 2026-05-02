package ru.hvostid.common.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthoritiesContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import ru.hvostid.common.http.SecurityHeaders;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Shared Spring Security pre-authentication support for gateway-injected headers.
 */
public final class GatewayPreAuthentication {
    private GatewayPreAuthentication() {
    }

    public static AuthenticationManager authenticationManager() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(GatewayPreAuthentication::loadPreAuthenticatedUser);
        provider.setThrowExceptionWhenTokenRejected(true);
        return new ProviderManager(provider);
    }

    public static RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter(
            AuthenticationManager authenticationManager) {
        RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
        filter.setPrincipalRequestHeader(SecurityHeaders.USER_ID);
        filter.setExceptionIfHeaderMissing(false);
        filter.setAuthenticationManager(authenticationManager);
        filter.setSecurityContextRepository(new NullSecurityContextRepository());
        filter.setAuthenticationDetailsSource(request -> new PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails(
                request,
                parseAuthorities(request.getHeader(SecurityHeaders.USER_ROLES))
        ));
        return filter;
    }

    public static long currentUserId(UserDetails user) {
        return parseUserId(user.getUsername());
    }

    public static long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw new BadCredentialsException("Invalid user id");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid user id", ex);
        }
    }

    public static List<GrantedAuthority> parseAuthorities(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }

        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(GatewayPreAuthentication::parseAuthority)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private static String parseAuthority(String role) {
        try {
            return UserRole.fromValue(role).authority();
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid user role", ex);
        }
    }

    private static UserDetails loadPreAuthenticatedUser(PreAuthenticatedAuthenticationToken token)
            throws AuthenticationException {
        long userId = parseUserId(token.getName());
        return User.withUsername(Long.toString(userId))
                .authorities(resolveAuthorities(token))
                .build();
    }

    private static Collection<? extends GrantedAuthority> resolveAuthorities(PreAuthenticatedAuthenticationToken token) {
        if (token.getDetails() instanceof GrantedAuthoritiesContainer container) {
            return container.getGrantedAuthorities();
        }
        return List.of();
    }
}
