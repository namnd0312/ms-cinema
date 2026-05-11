package com.namnd.cinema.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Strips a single trailing slash from the request URI so `/api/foo/` works
 * the same as `/api/foo` under Spring 6's strict PathPatternParser. Mirrors
 * the filter shipped from jwt-auth-autoconfigure for the other 5 services
 * (auth-service does not depend on that module).
 */
@Configuration
public class TrailingSlashWebMvcConfig {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> trailingSlashStripperFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws ServletException, IOException {
                String uri = req.getRequestURI();
                if (uri != null && uri.length() > 1 && uri.endsWith("/")) {
                    String stripped = uri.substring(0, uri.length() - 1);
                    chain.doFilter(new StrippedUriRequest(req, stripped), res);
                    return;
                }
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("trailingSlashStripperFilter");
        return reg;
    }

    private static final class StrippedUriRequest extends HttpServletRequestWrapper {
        private final String strippedUri;

        StrippedUriRequest(HttpServletRequest delegate, String strippedUri) {
            super(delegate);
            this.strippedUri = strippedUri;
        }

        @Override
        public String getRequestURI() {
            return strippedUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer original = super.getRequestURL();
            int q = original.indexOf("?");
            String base = q >= 0 ? original.substring(0, q) : original.toString();
            if (base.length() > 1 && base.endsWith("/")) {
                return new StringBuffer(base.substring(0, base.length() - 1));
            }
            return new StringBuffer(base);
        }

        @Override
        public String getServletPath() {
            String sp = super.getServletPath();
            if (sp != null && sp.length() > 1 && sp.endsWith("/")) {
                return sp.substring(0, sp.length() - 1);
            }
            return sp;
        }
    }
}
