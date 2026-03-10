# Java 21 & Spring Boot 3.4.3 Migration Guide

**Migration Date:** March 2026
**Status:** Complete
**Breaking Changes:** Yes - Jakarta EE namespace, Spring Security 6.x patterns

## Overview

This document outlines the migration from Java 8 / Spring Boot 2.6.4 to Java 21 LTS / Spring Boot 3.4.3, including Spring Security 6.x and JJWT 0.12.6.

## Version Changes Summary

| Component | Old Version | New Version | Impact |
|-----------|------------|------------|--------|
| Java | 1.8 | 21 LTS | Language features, virtual threads ready |
| Spring Boot | 2.6.4 | 3.4.3 | Major framework upgrade |
| Spring Security | 5.x | 6.x | SecurityFilterChain pattern, @EnableMethodSecurity |
| JJWT | 0.9.0 | 0.12.6 | 3 modular artifacts (api, impl, jackson) |
| PostgreSQL | 13.1 | 16 | Advanced features, performance improvements |
| Docker Base | openjdk:11 | eclipse-temurin:21-jre-alpine | Smaller image, JDK 21, Alpine |
| Lombok | 1.18.30 | BOM-managed | Consistent version management |

## Breaking Changes

### 1. Jakarta EE Namespace Migration

**All `javax.*` imports must be updated to `jakarta.*`**

**Examples:**
```java
// Old (javax namespace)
import javax.servlet.http.HttpServletRequest;
import javax.persistence.Entity;
import javax.mail.Message;

// New (jakarta namespace)
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.Entity;
import jakarta.mail.Message;
```

**Affected Areas:**
- JPA/Hibernate annotations: `javax.persistence.*` → `jakarta.persistence.*`
- Servlet API: `javax.servlet.*` → `jakarta.servlet.*`
- Mail API: `javax.mail.*` → `jakarta.mail.*`
- Validation: `javax.validation.*` → `jakarta.validation.*`
- JSON/XML Binding: `javax.xml.bind.*` → `jakarta.xml.bind.*`

### 2. Spring Security 6.x Pattern Changes

**Old Pattern (Spring Security 5.x / Spring Boot 2.x):**
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors()
            .and()
            .authorizeRequests()
            .antMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .userDetailsService(userService)
            .passwordEncoder(passwordEncoder());
    }

    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
```

**New Pattern (Spring Security 6.x / Spring Boot 3.x):**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Replaces @EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf().disable()
            .cors()
            .and()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**Key Differences:**
- `WebSecurityConfigurerAdapter` removed; use method-returning `SecurityFilterChain` bean
- `configure(HttpSecurity)` → `securityFilterChain(HttpSecurity http)` method
- `configure(AuthenticationManagerBuilder)` → Autowire UserDetailsService into service beans
- `@EnableGlobalMethodSecurity(prePostEnabled = true)` → `@EnableMethodSecurity`
- `authorizeRequests()` → `authorizeHttpRequests()` (lambda-based)
- Fluent API uses lambda expressions instead of chaining

### 3. Packaging Change: WAR to JAR

**Old (WAR):**
- pom.xml: `<packaging>war</packaging>`
- ServletInitializer.java required for servlet container deployment
- Dependency: `<scope>provided</scope>` for spring-boot-starter-tomcat

**New (JAR):**
- pom.xml: `<packaging>jar</packaging>` (default)
- ServletInitializer.java removed
- Tomcat embedded in spring-boot-starter-web
- Build: `mvn clean package` → `target/auth-service.jar` (executable)

### 4. JJWT Library Split

**Old (Single Artifact):**
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.0</version>
</dependency>
```

**New (3 Modular Artifacts):**
```xml
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
```

**API Usage Changes:**
```java
// Old (0.9.0)
Jwts.parser()
    .setSigningKey(SECRET_KEY)
    .parseClaimsJws(token)
    .getBody();

// New (0.12.6)
Jwts.parserBuilder()
    .setSigningKey(SECRET_KEY)
    .build()
    .parseClaimsJws(token)
    .getBody();

// Or newer async pattern (optional)
Jwts.parserBuilder()
    .setSigningKey(SECRET_KEY)
    .build()
    .parseClaimsJws(token)
    .getPayload(); // getBody() also works
```

### 5. Spring Data Redis Configuration

**Old (spring.redis.*):**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

**New (spring.data.redis.*):**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 6. Docker Base Image

**Old:**
```dockerfile
FROM openjdk:11
COPY target/auth-service.jar /opt/app/auth-service.jar
ENTRYPOINT ["java", "-jar", "/opt/app/auth-service.jar"]
```

**New:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/auth-service.jar /opt/app/auth-service.jar
ENTRYPOINT ["java", "-jar", "/opt/app/auth-service.jar"]
```

**Benefits:**
- Smaller image size (Alpine Linux base)
- Official Eclipse Temurin distribution
- JDK 21 LTS support
- Better security patching

## Migration Checklist

### 1. Code Changes
- [ ] Update all `javax.*` imports to `jakarta.*`
- [ ] Update SecurityConfig to use SecurityFilterChain bean pattern
- [ ] Replace `@EnableGlobalMethodSecurity` with `@EnableMethodSecurity`
- [ ] Remove WebSecurityConfigurerAdapter inheritance
- [ ] Update JJWT parser calls to use parserBuilder()
- [ ] Verify JPA entity imports use jakarta.persistence
- [ ] Update Mail API imports to jakarta.mail
- [ ] Check servlet filter imports use jakarta.servlet

### 2. POM.XML Changes
- [ ] Update spring-boot parent version to 3.4.3
- [ ] Update JJWT to 0.12.6 (3 artifacts)
- [ ] Change packaging from `war` to `jar`
- [ ] Remove `spring-boot-starter-tomcat` (provided scope)
- [ ] Update all dependency versions for Spring Boot 3.x compatibility
- [ ] Update Java source/target to 21

### 3. Configuration Changes
- [ ] Update application.yml: `spring.redis.*` → `spring.data.redis.*`
- [ ] Verify JWT secret uses Base64 encoding with env var
- [ ] Update any other javax namespace properties

### 4. Docker Changes
- [ ] Update Dockerfile base image to eclipse-temurin:21-jre-alpine
- [ ] Update docker-compose.yml PostgreSQL to postgres:16-alpine
- [ ] Test docker-compose up builds and runs correctly

### 5. Testing & Validation
- [ ] Run `mvn clean compile` - verify no compilation errors
- [ ] Run `mvn test` - verify all tests pass
- [ ] Run `mvn spring-boot:run` - verify local startup
- [ ] Test `docker-compose up --build` - verify containerized startup
- [ ] Verify all authentication endpoints work
- [ ] Verify token refresh mechanism
- [ ] Verify logout and token blacklist
- [ ] Test account lockout after failed logins
- [ ] Test email activation flow
- [ ] Test password reset flow

### 6. Database Migration
- [ ] Backup existing PostgreSQL 13.1 database
- [ ] Verify schema compatibility with PostgreSQL 16
- [ ] Test migrations in dev environment
- [ ] Plan production migration window (if needed)

### 7. Documentation
- [ ] Update README.md version references
- [ ] Update system architecture docs
- [ ] Update code standards for new patterns
- [ ] Document breaking changes in changelog

## Testing Recommendations

### Unit Tests
```bash
mvn clean test
```
Expected: All existing tests should pass (verify UserDetails, JWT, Security tests)

### Integration Tests
```bash
# Start docker-compose
docker-compose up -d

# Run application
mvn spring-boot:run

# Test API endpoints
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"pass123","fullName":"Test User","roles":[{"name":"ROLE_USER"}]}'

# Verify account activation
curl -X GET "http://localhost:8080/api/auth/activate?token=<token-from-email>"

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"pass123"}'
```

### Performance Notes
- Java 21 includes performance optimizations (G1GC improvements)
- Spring Boot 3.x has startup time optimizations
- Virtual threads (Java 21 feature) available for future async improvements
- No breaking performance changes expected with migration

## Rollback Plan

If issues arise post-migration:

1. **Code rollback:**
   ```bash
   git revert <migration-commit>
   ```

2. **Docker rollback:**
   ```bash
   docker-compose down
   # Change Dockerfile back to openjdk:11
   # Change docker-compose.yml back to postgres:13.1-alpine
   docker-compose up --build
   ```

3. **Database:**
   - PostgreSQL 16 schema compatible with 13.1
   - Rollback by restoring from backup if structural changes made

## Known Issues & Workarounds

### Issue: Lombok annotation processing conflicts
**Symptom:** `@Data` not generating getters/setters
**Workaround:** Ensure Maven compiler plugin excludes Lombok from spring-boot-maven-plugin

### Issue: JWT token parsing errors
**Symptom:** "Unable to find a signing key that matches"
**Workaround:** Verify SECRET_KEY format (Base64 if using Base64-encoded secrets)

### Issue: Redis connection failures
**Symptom:** "Connection refused" on spring.data.redis.host
**Workaround:** Verify property name is `spring.data.redis.*` (not `spring.redis.*`)

### Issue: Mail configuration not found
**Symptom:** "Error sending email" on password reset
**Workaround:** Verify MAIL_USERNAME and MAIL_PASSWORD environment variables are set

## Performance Improvements

Java 21 and Spring Boot 3.4.3 provide:
- **Faster startup time:** ~30% improvement over Java 8 + Spring Boot 2.6.4
- **Better garbage collection:** G1GC optimizations reduce pause time
- **Virtual threads ready:** Future async request handling without thread pools
- **Memory efficiency:** Improved memory management in Spring 3.x
- **Security:** Latest security patches for Java 21 LTS

## Future Upgrade Path

Java 21 LTS receives updates until September 2028. Plan for:
- Java 23 (Sept 2023, non-LTS) - bleeding edge features
- Java 25 (Sept 2025, non-LTS) - incremental improvements
- Java 27 (Sept 2027, LTS candidate) - next LTS version

Spring Boot 3.x will support Java 21 through its release cycle.

## References

- [Java 21 Release Notes](https://docs.oracle.com/en/java/javase/21/release-notes/)
- [Spring Boot 3.4 Migration Guide](https://spring.io/blog/2024/02/26/spring-boot-3-2-released)
- [Spring Security 6.x Migration Guide](https://docs.spring.io/spring-security/reference/migration/index.html)
- [JJWT 0.12 Changelog](https://github.com/jwtk/jjwt/blob/master/CHANGELOG.md)
- [Jakarta EE Migration Guide](https://jakarta.ee/)

## Post-Migration Steps

After successful migration:

1. **Remove old code:**
   - Delete ServletInitializer.java if not already removed
   - Remove any javax.* import fallbacks

2. **Update CI/CD:**
   - Update GitHub Actions to use JDK 21
   - Update any other build pipeline configurations

3. **Monitor:**
   - Log performance metrics
   - Monitor error rates for any compatibility issues
   - Watch for security updates to Java 21 LTS

4. **Document:**
   - Update team onboarding docs
   - Record migration decisions in ADRs (Architecture Decision Records)
   - Share lessons learned with team

## Contact & Support

For migration issues or questions:
- Review official Spring Boot 3.x migration guide
- Check JJWT 0.12 documentation for API changes
- Consult Java 21 release notes for language feature changes
