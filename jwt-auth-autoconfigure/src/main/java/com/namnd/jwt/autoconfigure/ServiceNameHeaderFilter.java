package com.namnd.jwt.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds X-Service-Name header to all HTTP responses.
 * Value is the spring.application.name of the consuming service.
 * Helps developers identify which microservice handled a request
 * from the browser Network tab.
 */
public class ServiceNameHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Service-Name";

    private final String serviceName;

    public ServiceNameHeaderFilter(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(HEADER_NAME, serviceName);
        filterChain.doFilter(request, response);
    }
}
