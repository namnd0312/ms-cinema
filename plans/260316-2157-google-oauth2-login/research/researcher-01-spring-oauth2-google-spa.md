# Spring Boot 3.4 OAuth2 Google Integration for SPA

## Overview
Spring Boot 3.4 + Spring Security support native OAuth2 client configuration for Google. For SPA architectures, backend handles authorization code exchange and issues custom JWT tokens, enabling both form/JWT and OAuth2 login flows simultaneously.

## Key Findings

### 1. Spring Boot 3.4 Google OAuth2 Configuration

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**YAML Configuration (application.yml):**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,profile,email
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
        provider:
          google:
            issuer-uri: https://accounts.google.com
            user-name-attribute: sub
```

**Google Console Setup:** Authorized redirect URI must be `http://localhost:8080/login/oauth2/code/google` (matches Spring's default endpoint).

### 2. SPA Authorization Code Flow (Backend-Centric)

**Flow Architecture:**
1. Frontend redirects to `/oauth2/authorization/google`
2. Spring Security handles auth code exchange internally
3. Custom success handler intercepts OAuth2 authentication
4. Backend exchanges OAuth2 token → custom JWT
5. Backend redirects to frontend with JWT query param/cookie
6. Frontend uses JWT for subsequent API calls

**Advantage:** No OAuth2 token exposure to frontend; backend maintains control; seamless JWT integration.

### 3. SecurityFilterChain for Dual Auth

**Pattern:** Support both form/JWT login AND OAuth2 login on same endpoint chain:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/public/**", "/login", "/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authEP -> authEP.baseUri("/oauth2/authorization"))
                .redirectionEndpoint(redirEP -> redirEP.baseUri("/login/oauth2/code/*"))
                .successHandler(oauth2SuccessHandler())
            )
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .csrf(csrf -> csrf.disable()); // Adjust per requirements

        return http.build();
    }
}
```

### 4. Custom OAuth2 Success Handler for JWT Issuance

**Implementation Pattern:**

```java
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication)
            throws ServletException, IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        // Extract user info from OAuth2 provider
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        // Generate custom JWT
        String jwt = jwtTokenProvider.generateToken(email, name);

        // Redirect with JWT (frontend picks up via query param or cookie)
        String redirectUrl = "http://localhost:3000/auth/callback?token=" + jwt;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
```

### 5. Key Dependencies

```xml
<!-- Spring Security OAuth2 Client -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-client</artifactId>
</dependency>

<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

## Critical Considerations

- **PKCE:** Use PKCE for public SPAs; Spring OAuth2 Client supports automatic PKCE generation
- **Token Redirect:** Use secure HttpOnly cookies or query params with short TTL; avoid exposing OAuth2 tokens to frontend
- **User Persistence:** Create/update user record during first OAuth2 login to link OAuth2 identity to app user
- **Logout:** Revoke OAuth2 tokens server-side before clearing JWT
- **CORS:** Configure CORS for frontend → backend redirect callback

## Sources
- [Spring Boot and OAuth2 - Spring Guide](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Spring Security Core Configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- [Authorization Grant Support](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html)
- [OAuth2 Backend for Frontend Pattern - Baeldung](https://www.baeldung.com/spring-cloud-gateway-bff-oauth2)
- [Custom JWT with Spring Security OAuth2](https://sultanov.dev/blog/custom-jwt-claims-in-spring-security-oauth/)
- [PKCE for SPA Authentication](https://www.javacodegeeks.com/secure-spa-authentication-with-pkce-and-spring-authorization-server.html)
