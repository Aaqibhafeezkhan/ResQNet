package dev.resqnet.backend.security;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with an identifier that appears in the audit trail.
 *
 * <p>Without this, "who did what" stops being answerable the moment two
 * responders are working at once.
 */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Request attribute and response header name. */
    public static final String HEADER = "X-ResQNet-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String requestId = supplied == null || supplied.isBlank()
                ? UUID.randomUUID().toString()
                : supplied.substring(0, Math.min(64, supplied.length()));
        request.setAttribute(HEADER, requestId);
        response.setHeader(HEADER, requestId);
        chain.doFilter(request, response);
    }
}
