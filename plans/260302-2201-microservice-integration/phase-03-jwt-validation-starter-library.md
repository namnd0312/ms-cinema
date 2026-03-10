# Phase 3: Create JWT Validation Starter Library

## Context Links
- [Current JwtAuthenticationFilter](/src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java)
- [Current JwtService](/src/main/java/com/namnd/springjwt/service/JwtService.java)
- [Plan overview](./plan.md)
- [Phase 2 - roles in JWT claims](./phase-02-new-auth-endpoints-validate-token-and-userinfo.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 3h
- **Depends on:** Phase 1 (module structure), Phase 2 (roles in JWT claims)
- Build a Spring Boot starter that downstream microservices add as a dependency to automatically validate JWT tokens and populate SecurityContext with user identity + roles -- NO database or UserDetailsService required.

## Key Insights
- Downstream services do NOT have access to users DB. They rely entirely on JWT claims for identity/roles.
- Unlike auth-service's filter (which loads UserDetails from DB + checks blacklist), the starter filter only parses JWT claims.
- Blacklist checking is optional in starter -- downstream services can call auth-service's `/api/auth/validate-token` for blacklist-aware validation, or just trust the JWT signature + expiry (simpler, recommended for most cases).
- Spring Boot 3.x auto-configuration uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (NOT `spring.factories`).
- Two-module pattern: `autoconfigure` (logic) + `starter` (thin dependency wrapper).

## Requirements

### Functional
- Consumer service adds `jwt-auth-spring-boot-starter` dependency, sets `jwt.auth.secret` property, gets auto-configured JWT filter + SecurityFilterChain
- Filter extracts Bearer token, validates signature/expiry, reads `sub`, `roles`, `userId` claims
- SecurityContext populated with `UsernamePasswordAuthenticationToken` containing email as principal and `SimpleGrantedAuthority` list from roles claim
- Consumer can override SecurityFilterChain via `@ConditionalOnMissingBean`
- Consumer can disable starter via `jwt.auth.enabled=false`

### Non-functional
- Zero transitive dependencies on JPA, PostgreSQL, Redis, Mail (starter must be lightweight)
- Starter JAR < 50KB
- No Spring Boot plugin (not executable JAR, just library JAR)

## Architecture

### Module Structure
```
jwt-auth-spring-boot-autoconfigure/
├── pom.xml
└── src/main/java/com/namnd/jwt/autoconfigure/
│   ├── JwtAuthProperties.java             (@ConfigurationProperties)
│   ├── JwtTokenValidator.java             (parse + validate JWT)
│   ├── JwtAuthenticationFilter.java       (OncePerRequestFilter)
│   ├── JwtAuthenticatedUser.java          (simple principal object)
│   └── JwtAutoConfiguration.java          (@AutoConfiguration)
└── src/main/resources/
    └── META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports

jwt-auth-spring-boot-starter/
├── pom.xml                                (only depends on autoconfigure)
└── (no source code)
```

### Package Name
Use `com.namnd.jwt.autoconfigure` (separate from `com.namnd.springjwt` to avoid component scan overlap if both are on classpath in auth-service tests).

### Filter Behavior
```
Request → Extract "Authorization: Bearer xxx"
  → null? → skip, continue filter chain
  → parse JWT with shared secret
  → invalid/expired? → skip (let Spring Security handle 401)
  → valid? → extract sub, roles, userId
  → create Authentication with authorities
  → set SecurityContextHolder
  → continue filter chain
```

## Related Code Files

### Files to Create

**jwt-auth-spring-boot-autoconfigure module:**
- `jwt-auth-spring-boot-autoconfigure/pom.xml`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticatedUser.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**jwt-auth-spring-boot-starter module:**
- `jwt-auth-spring-boot-starter/pom.xml`

## Implementation Steps

### 1. Populate jwt-auth-spring-boot-autoconfigure/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>jwt-auth-spring-boot-autoconfigure</artifactId>
    <name>JWT Auth Spring Boot Autoconfigure</name>

    <dependencies>
        <!-- Spring Boot (provided -- consumer brings these) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- JJWT (versions from parent dependencyManagement) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- For @ConfigurationProperties annotation processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <!-- NO spring-boot-maven-plugin (this is a library, not executable) -->
</project>
```

### 2. Create JwtAuthProperties

```java
package com.namnd.jwt.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt.auth")
public class JwtAuthProperties {
    /** Base64-encoded HS512 secret key (must match auth-service) */
    private String secret;
    /** Enable/disable JWT auto-configuration */
    private boolean enabled = true;
    /** Paths to skip JWT validation (comma-separated ant patterns) */
    private String[] publicPaths = {};

    // getters + setters (no Lombok -- keep starter dependency-free)
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String[] getPublicPaths() { return publicPaths; }
    public void setPublicPaths(String[] publicPaths) { this.publicPaths = publicPaths; }
}
```

### 3. Create JwtTokenValidator

```java
package com.namnd.jwt.autoconfigure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;

public class JwtTokenValidator {

    private final SecretKey signingKey;

    public JwtTokenValidator(String base64Secret) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Parse and validate token. Returns null if invalid/expired. */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : Collections.emptyList();
    }
}
```

### 4. Create JwtAuthenticatedUser

Simple principal object (no dependency on auth-service's UserPrinciple):
```java
package com.namnd.jwt.autoconfigure;

import java.util.List;

/** Lightweight principal extracted from JWT claims. */
public record JwtAuthenticatedUser(
    Long userId,
    String email,
    List<String> roles
) {}
```

### 5. Create JwtAuthenticationFilter (starter version)

```java
package com.namnd.jwt.autoconfigure;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lightweight JWT filter for downstream services.
 * Extracts identity + roles from JWT claims only (no DB lookup).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator tokenValidator;

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = extractToken(request);
        if (jwt != null) {
            Claims claims = tokenValidator.parseClaims(jwt);
            if (claims != null) {
                String email = tokenValidator.getEmail(claims);
                Long userId = tokenValidator.getUserId(claims);
                List<String> roles = tokenValidator.getRoles(claims);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                JwtAuthenticatedUser principal =
                    new JwtAuthenticatedUser(userId, email, roles);

                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                auth.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

### 6. Create JwtAutoConfiguration

```java
package com.namnd.jwt.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jwt.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtAuthProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenValidator jwtTokenValidator(JwtAuthProperties properties) {
        return new JwtTokenValidator(properties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtAuthenticationFilter")
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenValidator tokenValidator) {
        return new JwtAuthenticationFilter(tokenValidator);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            JwtAuthProperties properties) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                for (String path : properties.getPublicPaths()) {
                    auth.requestMatchers(path).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

### 7. Register auto-configuration

Create file:
`jwt-auth-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

Content:
```
com.namnd.jwt.autoconfigure.JwtAutoConfiguration
```

### 8. Populate jwt-auth-spring-boot-starter/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>jwt-auth-spring-boot-starter</artifactId>
    <name>JWT Auth Spring Boot Starter</name>

    <dependencies>
        <dependency>
            <groupId>com.namnd</groupId>
            <artifactId>jwt-auth-spring-boot-autoconfigure</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

### 9. Verify build
```bash
mvn clean install
```

### 10. Consumer usage example (for docs, not implementation)
```xml
<!-- Consumer service pom.xml -->
<dependency>
    <groupId>com.namnd</groupId>
    <artifactId>jwt-auth-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
```yaml
# Consumer service application.yml
jwt:
  auth:
    secret: ${JWT_SECRET}  # same secret as auth-service
    public-paths:
      - /actuator/health
      - /api/public/**
```

## Todo List
- [ ] Populate `jwt-auth-spring-boot-autoconfigure/pom.xml` with dependencies
- [ ] Create `JwtAuthProperties` with secret, enabled, publicPaths
- [ ] Create `JwtTokenValidator` with claims parsing
- [ ] Create `JwtAuthenticatedUser` record
- [ ] Create starter `JwtAuthenticationFilter` (claims-only, no DB)
- [ ] Create `JwtAutoConfiguration` with conditional beans
- [ ] Create `AutoConfiguration.imports` registration file
- [ ] Populate `jwt-auth-spring-boot-starter/pom.xml`
- [ ] Run `mvn clean install` from root
- [ ] Verify autoconfigure JAR contains META-INF registration

## Success Criteria
- `mvn clean install` builds all three modules (auth-service, autoconfigure, starter)
- Autoconfigure JAR contains `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- A consumer service with the starter dependency + `jwt.auth.secret` property auto-configures JWT security
- Consumer can override SecurityFilterChain with own bean
- Consumer can disable with `jwt.auth.enabled=false`
- Starter JAR has no transitive dependency on JPA, PostgreSQL, Redis, Mail

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Package scan overlap with auth-service | Medium | Use separate package `com.namnd.jwt.autoconfigure` |
| Auth-service's own SecurityConfig conflicts with starter on classpath | Low | Auth-service doesn't depend on starter; they share parent only |
| Missing JJWT runtime deps in consumer | Medium | Starter transitively includes via autoconfigure |
| Consumer overrides SecurityFilterChain but forgets JWT filter | Low | Document that override must re-add filter manually |

## Security Considerations
- Starter trusts JWT signature + expiry only. Does NOT check blacklist (no Redis dependency). Acceptable tradeoff: blacklisted tokens expire in 15 min max.
- If blacklist checking needed, consumer can call `POST /api/auth/validate-token` on auth-service.
- Secret key must be identical across all services -- Config Server centralizes this (Phase 4/5).
- Starter does not log token values (only log parse errors).

## Next Steps
- Phase 4: Add Spring Cloud dependencies to auth-service
- Phase 5: Set up Config Server to centralize `jwt.auth.secret`
- Future: Publish starter to Maven Central or private Nexus for external consumers
