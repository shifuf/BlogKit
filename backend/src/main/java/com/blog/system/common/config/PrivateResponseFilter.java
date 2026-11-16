package com.blog.system.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Browser/shared caches cannot store authenticated data or password unlock responses. */
@Component
public class PrivateResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/actuator/")) {
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        chain.doFilter(request, response);
    }
}
