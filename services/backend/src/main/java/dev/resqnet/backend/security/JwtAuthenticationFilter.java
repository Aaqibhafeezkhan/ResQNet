package dev.resqnet.backend.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves a bearer token into an authenticated principal.
 *
 * <p>Authorities are derived from the token's verified role, never from
 * anything the request says. A request that asks for {@code ROLE_ADMIN} in a
 * header gets nothing.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokens;

    public JwtAuthenticationFilter(AuthTokenService authTokens) {
        this.authTokens = authTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authTokens.resolveBearer(header.substring(BEARER_PREFIX.length()).trim())
                    .ifPresent(principal -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority(
                                        "ROLE_" + principal.role().name())));
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
