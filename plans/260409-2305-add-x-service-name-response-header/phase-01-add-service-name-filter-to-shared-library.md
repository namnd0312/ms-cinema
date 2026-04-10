# Phase 01: Add ServiceNameHeaderFilter to Shared Library

## Context Links
- [Scout Report](./reports/scout-report.md)
- [JwtAutoConfiguration.java](../jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java)
- [JwtAuthenticationFilter.java](../jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Create `ServiceNameHeaderFilter` in shared lib, register as auto-configured bean

## Key Insights
- `JwtAuthenticationFilter` extends `OncePerRequestFilter` -- follow same pattern
- Auto-config uses `@ConditionalOnMissingBean` -- allows per-service override if needed
- `spring.application.name` available via `@Value("${spring.application.name}")` in any Spring context
- Filter must run on ALL requests (not just authenticated), so register independently of security chain
- No Lombok in shared lib (confirmed from existing code style)

## Requirements

### Functional
- Add `X-Service-Name` response header to every HTTP response
- Header value = `spring.application.name` from consuming service

### Non-Functional
- Zero config required by consuming services
- Must not interfere with existing security filters
- Must not break if `spring.application.name` is missing (defensive)

## Architecture
```
Request --> NGINX Ingress --> ServiceNameHeaderFilter --> JwtAuthenticationFilter --> Controller
                              (adds X-Service-Name      (validates JWT)
                               to response)
```

Filter is a generic servlet filter (not part of Spring Security chain). Registered as `FilterRegistrationBean` to ensure it runs on all requests including public endpoints.

## Related Code Files

### Create
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/ServiceNameHeaderFilter.java`

### Modify
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java` (add bean)

## Implementation Steps

### Step 1: Create ServiceNameHeaderFilter.java
Location: `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/`

```java
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
```

### Step 2: Register bean in JwtAutoConfiguration.java
Add this bean method to `JwtAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean(ServiceNameHeaderFilter.class)
public FilterRegistrationBean<ServiceNameHeaderFilter> serviceNameHeaderFilter(
        @Value("${spring.application.name:unknown}") String applicationName) {
    FilterRegistrationBean<ServiceNameHeaderFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new ServiceNameHeaderFilter(applicationName));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setName("serviceNameHeaderFilter");
    return registration;
}
```

Key points:
- `FilterRegistrationBean` ensures filter runs on ALL requests (outside Spring Security chain)
- `Ordered.HIGHEST_PRECEDENCE` so header is set even if downstream filters throw
- Default value `"unknown"` if `spring.application.name` not set
- `@ConditionalOnMissingBean` allows override

### Step 3: Add required imports to JwtAutoConfiguration
```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
```

## Todo List
- [ ] Create `ServiceNameHeaderFilter.java`
- [ ] Add `FilterRegistrationBean` bean to `JwtAutoConfiguration.java`
- [ ] Add necessary imports

## Success Criteria
- `X-Service-Name` header present in all HTTP responses from all 6 services
- Header value matches each service's `spring.application.name`
- No compilation errors in shared lib or any consuming service
- Existing JWT auth behavior unaffected

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Header set before response committed | Low | `setHeader` before `doFilter` -- safe |
| Conflicts with service-level filters | Low | `@ConditionalOnMissingBean` allows override |
| Missing spring.application.name | Low | Default value `"unknown"` |

## Security Considerations
- `X-Service-Name` reveals internal service names -- acceptable for dev/debug
- If prod exposure is a concern, can add `@ConditionalOnProperty` toggle later (YAGNI for now)
- Header contains no sensitive data (just service name already visible in K8s service names)

## Next Steps
- Phase 02: Verify K8s Ingress passes header through
- Phase 03: Compile and test all services
