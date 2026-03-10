# JWT Spring Boot Starter Library — Research Report
Date: 2026-03-02 | Researcher: 02

---

## 1. Spring Boot 3.x Starter Module Structure

### Recommended Multi-Module Layout
```
jwt-auth-starter/                         ← parent pom (packaging=pom)
├── jwt-auth-spring-boot-autoconfigure/   ← auto-config logic + filter
│   ├── src/main/java/...
│   └── src/main/resources/
│       └── META-INF/spring/
│           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── jwt-auth-spring-boot-starter/         ← thin aggregator (no code)
    └── pom.xml
```

For simple starters, combine both modules into one. Spring recommends splitting only when consumers may want autoconfigure without the full starter dependency set.

### Parent POM skeleton
```xml
<groupId>com.namnd</groupId>
<artifactId>jwt-auth-starter-parent</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
<modules>
  <module>jwt-auth-spring-boot-autoconfigure</module>
  <module>jwt-auth-spring-boot-starter</module>
</modules>
```

### Autoconfigure module `pom.xml` deps
```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <optional>true</optional>           <!-- not transitive -->
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
<!-- annotation processor for metadata → faster startup -->
<build><plugins><plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration><annotationProcessorPaths><path>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure-processor</artifactId>
  </path></annotationProcessorPaths></configuration>
</plugin></plugins></build>
```

### Starter module `pom.xml` deps (thin wrapper)
```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>com.namnd</groupId>
    <artifactId>jwt-auth-spring-boot-autoconfigure</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>
```

---

## 2. AutoConfiguration Registration (Spring Boot 3.x)

File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
com.namnd.jwt.autoconfigure.JwtAutoConfiguration
```
`spring.factories` is deprecated in Boot 3.0. Do NOT use it.

---

## 3. ConfigurationProperties

```java
@ConfigurationProperties(prefix = "jwt.auth")
public class JwtAuthProperties {
    /** Whether to enable JWT authentication filter. Default true. */
    private boolean enabled = true;
    /** Base64-encoded HS512 secret key. */
    private String secretKey;
    /** Paths to exclude from JWT validation (ant patterns). */
    private List<String> publicPaths = List.of("/actuator/health");
    // getters/setters
}
```

---

## 4. JWT Validation Filter (reusable)

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtAuthProperties props;

    public JwtAuthenticationFilter(JwtAuthProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode(props.getSecretKey()));
                Claims claims = Jwts.parser()
                    .verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

                String subject = claims.getSubject();
                // Build minimal auth token — no UserDetailsService needed in downstream service
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        subject, null,
                        extractAuthorities(claims));          // parse "roles" claim
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private Collection<GrantedAuthority> extractAuthorities(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                .map(r -> new SimpleGrantedAuthority((String) r))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
```

Key: no `UserDetailsService` call — downstream microservices trust the token; authority from claims only.

---

## 5. Auto-Configuration Class

```java
@AutoConfiguration
@ConditionalOnClass({ JwtParser.class, HttpSecurity.class })
@ConditionalOnProperty(prefix = "jwt.auth", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtAuthProperties.class)
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtAuthProperties props) {
        return new JwtAuthenticationFilter(props);
    }

    /** Low-order chain so consumer's @Order(1) chain takes priority */
    @Bean
    @ConditionalOnMissingBean(name = "jwtSecurityFilterChain")
    @Order(SecurityProperties.ACCESS_OVERRIDE_ORDER - 5)   // = 1
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter filter) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths()).permitAll()
                .anyRequest().authenticated());
        return http.build();
    }

    private String[] publicPaths() {
        // resolved from JwtAuthProperties at bean creation time
        return new String[]{"/actuator/health"};
    }
}
```

### Override by consumer
Consumer defines own `SecurityFilterChain` bean → starter's `@ConditionalOnMissingBean(name="jwtSecurityFilterChain")` backs off automatically.

---

## 6. Security Override Strategy (Consumer Service)

```java
// In consumer microservice — completely replaces starter's chain
@Bean("jwtSecurityFilterChain")           // same name → ConditionalOnMissingBean fires
@Order(1)
public SecurityFilterChain customChain(HttpSecurity http,
                                        JwtAuthenticationFilter filter) throws Exception {
    http.securityMatcher("/api/**")
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        // custom rules...
        ;
    return http.build();
}
```

Because `@ConditionalOnMissingBean` checks by name, naming the consumer bean `"jwtSecurityFilterChain"` suppresses the starter's bean registration.

---

## 7. Spring Boot 3.x Migration Note

| Boot 2.x | Boot 3.x |
|---|---|
| `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `@Configuration` on auto-config | `@AutoConfiguration` (since 2.7, required in 3.0) |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean |

---

## Unresolved Questions

1. Should `jwtSecurityFilterChain` expose a `RequestMatcher` property so consumers can override the matched path prefix without rewriting the entire chain?
2. Token blacklist (Redis JTI check) — include in starter or leave to auth-service only? Pulling Redis into starter adds a heavy dependency.
3. Is `@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER - 5)` the right default, or should it be `@Order(Ordered.LOWEST_PRECEDENCE - 5)` to stay behind any consumer chain?

---

Sources:
- [Spring Boot — Developing Auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Baeldung — Custom Auto-Configuration](https://www.baeldung.com/spring-boot-custom-auto-configuration)
- [bplo.net — Spring Boot 3 Custom Starter](https://bplo.net/posts/spring-boot-3-custom-starter.html)
- [Baeldung — Creating a Custom Starter](https://www.baeldung.com/spring-boot-custom-starter)
- [Spring Security — OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
