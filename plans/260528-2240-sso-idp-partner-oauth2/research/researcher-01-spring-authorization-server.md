# Spring Authorization Server 1.4.x Embedded OIDC IdP Research

## 1. Coexisting SecurityFilterChains: AuthorizationServer + Form Login

**Architecture**: Two @Order beans in same app:
- `@Order(1)`: AuthorizationServerSecurityFilterChain — handles /oauth2/**, /.well-known/**
- `@Order(2)`: defaultSecurityFilterChain — form login + /api/** JWT filter

```java
@Configuration
@EnableAuthorizationServer  // Spring AS 1.4.x
public class AuthorizationServerConfig {
  
  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) 
      throws Exception {
    OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
    http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
        .oidc(Customizer.withDefaults());
    return http.build();
  }
  
  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) 
      throws Exception {
    http.authorizeRequests(authz -> authz
        .requestMatchers("/api/**").authenticated()
        .anyRequest().permitAll())
      .formLogin(Customizer.withDefaults())
      .oauth2Login(Customizer.withDefaults());  // Preserve Google login
    http.addFilterBefore(jwtAuthenticationFilter(), 
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
```

**Key Point**: Filter ordering critical — Spring AS @Order(1) processes /oauth2/** before @Order(2) sees it.

---

## 2. JPA-Backed Spring AS Services

Implement 3 core repositories — **avoid JdbcRegisteredClientRepository**:

```java
@Repository
public class JpaRegisteredClientRepository implements RegisteredClientRepository {
  private final RegisteredClientEntity repo;
  
  @Override
  public void save(RegisteredClient client) {
    // Serialize client settings (scopes, redirectUris) to JSON in DB
    RegisteredClientEntity entity = toEntity(client);
    repo.save(entity);
  }
  
  @Override
  public RegisteredClient findByClientId(String clientId) {
    return repo.findByClientId(clientId)
        .map(this::toClient)
        .orElse(null);
  }
}

@Repository
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {
  @Override
  public void save(OAuth2Authorization authorization) {
    // Persist: client_id, principal_name, access_token (encrypted), id_token, state
    AuthorizationEntity entity = toEntity(authorization);
    authRepo.save(entity);  // Use SerializationUtils for token serialization
  }
  
  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    return authRepo.findByAccessToken(token)
        .map(this::fromEntity).orElse(null);
  }
}
```

**Gotchas**: 
- Tokens are serializable objects → store as BLOB/JSON; use Jackson for serialization
- Settings JSON (redirectUris, scopes) requires custom deserializer
- Authorization must persist between token requests (for consent re-use)

---

## 3. RS256 JwtEncoder from Database RSA Key

```java
@Configuration
public class JwtEncoderConfig {
  
  @Bean
  public JWKSource<SecurityContext> jwkSource(RsaKeyRepository keyRepo) {
    return (jwkSetRequest, context) -> {
      List<JWK> jwks = new ArrayList<>();
      
      // Load ACTIVE + RETIRED keys for rotation window
      keyRepo.findByStatus(KeyStatus.ACTIVE).forEach(keyEntity -> {
        RSAKey rsaKey = new RSAKey.Builder(keyEntity.getPublicKey())
            .privateKey(keyEntity.getPrivateKey())
            .keyID(keyEntity.getKid())  // kid set automatically
            .algorithm(JWSAlgorithm.RS256)
            .build();
        jwks.add(rsaKey);
      });
      
      keyRepo.findByStatus(KeyStatus.RETIRED).forEach(keyEntity -> {
        RSAKey rsaKey = new RSAKey.Builder(keyEntity.getPublicKey())
            .privateKey(keyEntity.getPrivateKey())
            .keyID(keyEntity.getKid())
            .build();
        jwks.add(rsaKey);  // Allows token validation for grace period
      });
      
      return new JWKSet(jwks);
    };
  }
  
  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }
}
```

**kid Header Behavior**: NimbusJwtEncoder picks first RS256 ACTIVE key if multiple exist (rotation issue tracked in #1005). Workaround: only 1 ACTIVE at a time.

---

## 4. OAuth2TokenCustomizer for ID Token Claims

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(UserRepository userRepo) {
  return context -> {
    if (context.getTokenType() == OAuth2TokenType.ID_TOKEN) {
      String username = context.getPrincipal().getName();
      User user = userRepo.findByUsername(username).orElse(null);
      
      context.getClaims().claims(claims -> {
        claims.put("sub", user.getId());
        claims.put("email", user.getEmail());
        claims.put("email_verified", user.isEmailVerified());
        claims.put("name", user.getFullName());
      });
    }
    // access_token remains plain (no custom claims)
  };
}
```

**Context.getTokenType()** distinguishes ID_TOKEN vs ACCESS_TOKEN; inject claims only to ID_TOKEN.

---

## 5. /oauth2/authorize → /login with Existing UserDetailsService + Google

Flow: Unauthenticated → /oauth2/authorize redirects to /login (Spring AS default) → User selects email/password OR Google button.

**No conflicts** if:
1. Form login configured in @Order(2) chain (above)
2. oauth2Login() also in @Order(2)
3. Both use same UserDetailsService (Spring merges Principal)
4. Google SuccessHandler returns to /oauth2/authorize (browser redirect)

**Watch**: Google callback returns to registered redirect_uri; ensure /login is NOT a client redirect_uri (only /oauth2/code-callback).

---

## 6. Discovery Endpoint Customization

**Issuer URI** derived from `server.servlet.context-path` + `application.yml`:

```yaml
server:
  servlet:
    context-path: /auth  # issuer = https://yourdomain.com/auth
```

Override in @Bean:

```java
@Bean
public OidcProviderConfigurationEndpointFilter oidcProviderConfigurationEndpointFilter(
    RegisteredClientRepository clients) {
  return new OidcProviderConfigurationEndpointFilter(clients) {
    @Override
    protected OidcProviderConfiguration getProviderConfiguration() {
      OidcProviderConfiguration config = super.getProviderConfiguration();
      return OidcProviderConfiguration.builder()
          .issuer("https://explicit-issuer.com")  // Override here
          .build();
    }
  };
}
```

---

## 7. Spring AS 1.4.x & Spring Boot 3.4.x Compatibility

⚠️ **Critical Finding**: Search results show **version 1.4.x does NOT exist** as released version. Latest stable:
- Spring Authorization Server: **1.3.x** (May 2024)
- Spring Boot 3.4.x compatible: Yes (uses spring-security 6.x)

**Maven Coords** (for 1.3.x):
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
  <version>1.3.1</version>
</dependency>
```

Note: Artifact marked "deprecated" in favor of `spring-security-oauth2-authorization-server` (underlying lib).

---

## Unresolved Questions

1. **Version Clarification**: Confirm if "1.4.x" requirement is actual or typo for 1.3.x/7.x (underlying spring-security lib)?
2. **Multiple ACTIVE Keys**: How to handle kid rotation with NimbusJwtEncoder throwing exception on multiple RS256 keys (#1005 open)?
3. **Consent Cache**: Does Spring AS store consent per user/client/scope combo automatically, or manual implementation needed?
4. **Token Encryption**: Do id_tokens need encryption (JWE) or JWT signature (JWS) sufficient for OIDC-only flow?

---

## Sources
- [Spring Authorization Server Getting Started](https://docs.spring.io/spring-authorization-server/reference/getting-started.html)
- [Maven Repository: spring-boot-starter-oauth2-authorization-server](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-oauth2-authorization-server)
- [Spring Authorization Server Key Rotation Issue #1005](https://github.com/spring-projects/spring-authorization-server/issues/1005)
- [JWS + JWK in Spring Security OAuth2 - Baeldung](https://www.baeldung.com/spring-security-oauth2-jws-jwk)
- [How-to: JWK Source with Key Rotation - Issue #544](https://github.com/spring-projects/spring-authorization-server/issues/544)
