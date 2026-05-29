# Research: JWT Migration, OIDC Consent, Security Hardening
**Date:** 2026-05-28 | **Scope:** Spring Boot 3.4.3 + Spring Security 6.x + Angular 18

---

## A. Dual-Mode JWT Verification (HS512 legacy + RS256 new)

**Problem:** 5 resource services consume `jwt-auth-autoconfigure` starter verifying HS512 only. Need backward-compatible RS256 path via JWKS.

**Solution Pattern:**

1. **Pre-verification JWT header inspection** (SignedJWT peek):
```java
SignedJWT jwt = SignedJWT.parse(token);
String alg = jwt.getHeader().getAlgorithm().getName(); // "HS512" or "RS256"
String kid = (String) jwt.getHeader().get("kid");
```

2. **Nimbus JWKS configuration with caching:**
```java
// 1h cache, 30s remote timeout, refresh-on-miss
JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(
    new URL("https://auth-service/oauth2/jwks"),
    new DefaultResourceRetriever(30000, 30000)
);
NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
JWKSourceBuilder.buildOIDCJWKSourceBuilder(new URL(...)).cache(3600, 60).build();
```

3. **Dispatcher logic in autoconfigure:**
```java
@Configuration
public class DualModeJwtAuthenticationFilter extends OncePerRequestFilter {
    @Value("${jwt.dual-mode-disable-rs256:false}") private boolean disableRS256;
    private final JwtDecoder hs512Decoder; // HmacKey decoder
    private final JwtDecoder rs256Decoder; // NimbusJwtDecoder
    
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String token = extractToken(req);
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String alg = jwt.getHeader().getAlgorithm().getName();
            
            if ("RS256".equals(alg) && !disableRS256) {
                // RS256 path (with JWKS caching)
                Jwt decoded = rs256Decoder.decode(token);
            } else {
                // HS512 fallback (legacy)
                Jwt decoded = hs512Decoder.decode(token);
            }
        } catch (JwtException e) {
            // Log with trace_id for audit
        }
    }
}
```

4. **JWKS endpoint resilience:** RemoteJWKSet caches keys; downtime doesn't break verification until TTL expires. Expired JWKS → fall back to in-memory `RETIRED` key set for grace period.

5. **Rollback:** `JWT_DUAL_MODE_DISABLE_RS256=true` env var immediately disables RS256, enforces HS512-only (KISS approach).

**Key Detail:** Cache-Control header on JWKS endpoint: `max-age=3600, public` ensures predictable TTL.

---

## B. OIDC Consent Screen Patterns

**Architecture:** Spring Authorization Server (`OAuth2AuthorizationConsentService` JPA-backed) → custom consent page (Angular 18 + Material 18) → auto-approval for trusted partners.

**Data Model (deduplication key):**
```
oauth2_authorization_consent
  (principal_name, registered_client_id) → UNIQUE
  scopes: VARCHAR (comma-delimited or JSON array)
```

**Spring AS trigger:** `OAuth2AuthorizationConsentService.findByRegisteredClientIdAndPrincipalName()` returns empty → consent UI shown. Persisted via `save()`.

**Backend consent redirect (Spring AS config):**
```java
@Bean
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
    OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();
    configurer.consentPage("/oauth/consent"); // Custom endpoint
    http.apply(configurer);
    return http.build();
}
```
Spring passes `client_id`, `state`, `scopes` as query params → Angular receives, displays partner name + redirect URI hostname + scope friendly names.

**Angular 18 + Material consent component:**
```typescript
@Component({
  selector: 'app-consent',
  template: `
    <mat-card>
      <h2>{{ clientName }} requests access</h2>
      <p>Redirect: {{ redirectUriHostname }}</p>
      <mat-selection-list [formControl]="scopesCtrl">
        <mat-list-option *ngFor="let s of scopesFriendly">
          {{ s.label }}
        </mat-list-option>
      </mat-selection-list>
      <button (click)="approve()">Allow</button>
      <button (click)="deny()">Deny</button>
    </mat-card>
  `
})
export class ConsentComponent implements OnInit {
  clientName: string;
  redirectUriHostname: string; // Extract from client.redirectUris[0], hostname only
  
  ngOnInit() {
    // CRITICAL: Load clientName from DB only, reject request params
    this.clientService.getClientName(this.route.snapshot.queryParams['client_id'])
      .subscribe(name => this.clientName = name);
  }
}
```

**Defense vs spoofing:** Never display `request.getParameter('client_name')` or similar. Only display `RegisteredClient.client_name` fetched from repository.

**Auto-approve (trusted partners):**
```java
if (registeredClient.getClientSettings().isAutoApprove()) {
    consentService.save(
        new OAuth2AuthorizationConsent(clientId, principalName, scopes)
    );
    // Skip consent UI, continue auth flow
}
```

---

## C. OIDC Security Hardening Checklist

**1. PKCE mandatory for all clients** (RFC 9700, Jan 2025):
```java
ClientSettings.builder().requireProofKey(true).build(); // Even confidential clients
```

**2. Redirect URI validation:**
- Exact string match (no wildcards).
- HTTPS-only, except `localhost:*` and `127.0.0.1:*`.
- No fragments (`#`).
- Stored in DB, never accept from request params.

**3. Refresh token rotation + reuse detection:**
```java
TokenSettings.builder()
    .reuseRefreshTokens(false) // Rotate on every refresh
    .refreshTokenTimeToLive(Duration.ofDays(7))
    .build();
```
Spring AS invalidates old token chain on reuse attempt (theft detection).

**4. JWKS cache policy:** `Cache-Control: max-age=3600, public` (1h default). Clients must refresh JWKS every hour or on `kid` miss.

**5. Rate limiting per `/oauth2/token`:**
- **K8s NGINX Ingress**: `nginx.ingress.kubernetes.io/limit-rps: "10"` (global) + `limit-connections: "5"` (per IP).
- **In-app (Bucket4j)**: Better for per-client-id limiting; overhead: adds latency ~2ms per request. **Recommendation for 1–5 partners:** Use NGINX layer (stateless, ops-friendly) + in-app per-client circuit breaker.

**6. Key rotation runbook (RSA 2048, 90-day cadence):**
```
ACTIVE status (current signing key)
RETIRED status (old key, still validates tokens)
JWKS response: both ACTIVE + RETIRED
Keep RETIRED in JWKS for max(access_token_ttl, id_token_ttl) = max 1h
Timeline: Generate → Insert ACTIVE → Mark old ACTIVE as RETIRED → Wait 1h → Remove RETIRED
```

**7. Audit event schema:**
```json
{
  "event_type": "oauth2.token.issued|revoked|consent.granted|consent.denied|client.registered|client.secret_rotated",
  "client_id": "partner-oauth-client",
  "principal_name": "user@example.com",
  "scopes": ["openid", "email"],
  "success": true,
  "ip": "203.0.113.42",
  "user_agent": "Mozilla/5.0...",
  "trace_id": "abc123xyz789",
  "timestamp": "2026-05-28T10:30:00Z"
}
```
Index: `(event_type, client_id, timestamp)` for audit queries.

---

## Sources
- [Spring Authorization Server JPA Guide](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-jpa.html)
- [OAuth2AuthorizationConsentService API](https://docs.spring.io/spring-authorization-server/docs/current/api/org/springframework/security/oauth2/server/authorization/OAuth2AuthorizationConsentService.html)
- [RFC 9700: OAuth 2.0 Security Best Current Practice](https://sergiolema.dev/2026/04/13/spring-security-6-oauth-2-1-replacing-implicit-grant-and-ropc-with-pkce/)
- [OWASP OAuth2 Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)
- [Refresh Token Rotation & Reuse Detection](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/)
- [Auth0 Refresh Token Rotation Docs](https://dev.auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation)

---

## Unresolved Questions
1. **JWKS cache layer placement:** Remote caching (Redis) vs in-JVM (Caffeine)? For 5 services, in-JVM (KISS) sufficient, or Redis for cross-region consistency?
2. **Audit event persistence:** Kafka sink (event streaming) vs synchronous DB write? Recommend Kafka for non-blocking audit.
3. **Client secret rotation ceremony:** Manual (admin portal) vs automated (HashiCorp Vault integration)? Scope for next iteration.
