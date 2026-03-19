# Phase 2: Spring Security OAuth2 Configuration

## Context Links
- [Plan overview](./plan.md)
- [Phase 1: Schema](./phase-01-database-schema-oauth-provider.md)
- [Research: Spring OAuth2](./research/researcher-01-spring-oauth2-google-spa.md)
- SecurityConfig: `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java`

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Add OAuth2 Client dependency, configure Google provider in YAML, update SecurityFilterChain for dual auth (JWT + OAuth2)

## Key Insights
- **Stateless + OAuth2 conflict:** Spring OAuth2 login uses session to store authorization request (state param). Solution: use `HttpCookieOAuth2AuthorizationRequestRepository` to store state in cookies instead of session
- Keep `SessionCreationPolicy.STATELESS`; the cookie-based repo avoids session dependency
- OAuth2 endpoints (`/oauth2/authorization/**`, `/login/oauth2/code/**`) must be permitAll
- Existing JWT filter must NOT process OAuth2 callback URLs

## Requirements
### Functional
- Add `spring-boot-starter-oauth2-client` to pom.xml
- Configure Google client registration in application.yml with env vars
- SecurityFilterChain supports both JWT filter and `.oauth2Login()`
- Add configurable frontend callback URL

### Non-Functional
- No hardcoded secrets; use env vars for client-id/secret
- Cookie-based auth request repo for stateless compatibility

## Architecture
```
Browser -> /oauth2/authorization/google -> Spring redirects to Google
Google  -> /login/oauth2/code/google    -> Spring exchanges code
                                        -> OAuth2SuccessHandler (Phase 3)
                                        -> Redirect to frontend with JWT
```

## Related Code Files

### Modify
- `auth-service/pom.xml` -- add oauth2-client dependency
- `auth-service/src/main/resources/application.yml` -- add OAuth2 + frontend URL config
- `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` -- add oauth2Login

### Create
- `auth-service/src/main/java/com/namnd/cinema/config/security/HttpCookieOAuth2AuthorizationRequestRepository.java`

## Implementation Steps

### 1. Add dependency to `pom.xml`
After the `spring-boot-starter-security` dependency, add:

```xml
<!-- Spring Security OAuth2 Client (Google login) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

### 2. Add OAuth2 config to `application.yml`
Add under `spring:` section:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:}
            client-secret: ${GOOGLE_CLIENT_SECRET:}
            scope: openid,profile,email
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
```

Add under `namnd.app:` section:

```yaml
namnd:
  app:
    oauth2CallbackUrl: ${OAUTH2_CALLBACK_URL:http://localhost:4200/auth/oauth2/callback}
```

### 3. Create `HttpCookieOAuth2AuthorizationRequestRepository`
Purpose: stores OAuth2 authorization request in short-lived cookie (not session) for stateless compatibility.

File: `auth-service/src/main/java/com/namnd/cinema/config/security/HttpCookieOAuth2AuthorizationRequestRepository.java`

```java
package com.namnd.cinema.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import java.util.Base64;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeCookie(response);
            return;
        }
        String serialized = Base64.getUrlEncoder().encodeToString(
            SerializationUtils.serialize(authorizationRequest));
        Cookie cookie = new Cookie(COOKIE_NAME, serialized);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = getCookie(request);
        removeCookie(response);
        return authRequest;
    }

    private OAuth2AuthorizationRequest getCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                byte[] bytes = Base64.getUrlDecoder().decode(cookie.getValue());
                return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(bytes);
            }
        }
        return null;
    }

    private void removeCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
```

**Note:** `SerializationUtils.serialize` is deprecated in Spring 6. If compile warns, switch to Jackson-based serialization. Check at implementation time.

### 4. Update `SecurityConfig.java`
Inject the cookie repo and success handler (Phase 3). Add `.oauth2Login()` block.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    // constructor injection

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers(
                    "/api/auth/**",
                    "/oauth2/authorization/**",
                    "/login/oauth2/code/**",
                    "/actuator/health", "/actuator/info", "/actuator/prometheus",
                    "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(auth -> auth
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                )
                .redirectionEndpoint(redir -> redir
                    .baseUri("/login/oauth2/code/*")
                )
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(customAccesDeniedHandler())
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults());

        return http.build();
    }
    // ... rest unchanged
}
```

**Key change:** `oauth2Login()` block added between `sessionManagement` and `exceptionHandling`. Cookie repo replaces default session-based repo.

## Todo List
- [ ] Add `spring-boot-starter-oauth2-client` to pom.xml
- [ ] Add Google OAuth2 config to application.yml
- [ ] Add `oauth2CallbackUrl` to namnd.app config
- [ ] Create `HttpCookieOAuth2AuthorizationRequestRepository`
- [ ] Update `SecurityConfig` with oauth2Login
- [ ] Add OAuth2 paths to permitAll
- [ ] Compile and verify no errors

## Success Criteria
- Application starts with OAuth2 config (even without real Google credentials)
- `/oauth2/authorization/google` redirects to Google (when credentials provided)
- Existing JWT auth still works
- No session created for regular API calls

## Risk Assessment
- **Medium:** `SerializationUtils.serialize` deprecated in Spring 6 -- may need Jackson alternative
- **Low:** OAuth2 client auto-config might conflict with existing security beans
- **Mitigation:** Test boot startup early; switch serialization if needed

## Security Considerations
- Cookie is HttpOnly, 180s TTL, path=/
- State parameter handled automatically by Spring OAuth2 Client
- No PKCE needed since backend is confidential client (has client-secret)

## Next Steps
- Phase 3: Custom success handler that processes OAuth2 auth result
