# Scout Report: X-Service-Name Response Header

## Findings

### Shared Library (jwt-auth-autoconfigure)
- Package: `com.namnd.jwt.autoconfigure`
- Auto-config: `JwtAutoConfiguration.java` registered via Spring Boot 3.x `AutoConfiguration.imports`
- Existing beans: `JwtTokenValidator`, `JwtAuthenticationFilter`, `SecurityFilterChain`
- Properties: `JwtAuthProperties` (prefix `jwt.auth`) -- no Lombok
- Filter: `JwtAuthenticationFilter` extends `OncePerRequestFilter`, handles JWT parsing only
- No CORS config in shared lib

### Service Names (all 6 confirmed)
| Service | spring.application.name | Port |
|---------|------------------------|------|
| auth | auth-service | 8081 |
| movie | movie-service | 8082 |
| booking | booking-service | 8083 |
| payment | payment-service | 8084 |
| notification | notification-service | 8085 |
| audit | audit-service | 8086 |

### K8s Ingress
- NGINX Ingress Controller, path-based routing
- No custom header stripping annotations
- NGINX passes upstream response headers by default -- X-Service-Name will pass through

### CORS
- Auth-service: `cors(Customizer.withDefaults())` -- no explicit `exposedHeaders`
- Other 5 services: use shared lib's `SecurityFilterChain` which has NO cors config
- Browser JS won't see X-Service-Name unless `Access-Control-Expose-Headers` includes it
- However: Network tab shows ALL response headers regardless of CORS expose config
- CORS expose only affects `fetch()/XMLHttpRequest` JS access, NOT DevTools visibility

### Key Decision
- Network tab = visible without CORS changes
- If JS needs header access later, add CORS expose then (YAGNI)
