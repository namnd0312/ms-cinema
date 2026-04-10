# Phase 02: Verify K8s Ingress and CORS Config

## Context Links
- [Scout Report](./reports/scout-report.md)
- [Ingress config](../k8s/ingress.yml)
- [Auth SecurityConfig](../auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Confirm NGINX Ingress passes `X-Service-Name` and CORS config doesn't need changes

## Key Insights
- NGINX Ingress passes all upstream response headers by default
- `proxy_hide_header` would need to be explicitly set to strip headers -- not present
- No `nginx.ingress.kubernetes.io/configuration-snippet` annotations that could strip headers
- Browser Network tab shows ALL response headers regardless of CORS `Access-Control-Expose-Headers`
- CORS expose only matters for JS `fetch()`/`XMLHttpRequest` programmatic header access

## Requirements

### Functional
- `X-Service-Name` header visible in browser Network tab through NGINX Ingress

### Non-Functional
- No Ingress config changes needed
- No CORS config changes needed (per YAGNI -- Network tab is the stated use case)

## Architecture
```
Browser <-- X-Service-Name header <-- NGINX Ingress <-- upstream service response
```

NGINX default behavior: proxy_pass_header passes all upstream headers.

## Related Code Files

### Verify (no changes expected)
- `k8s/ingress.yml`

### Optional future changes (NOT now, YAGNI)
- Auth-service `SecurityConfig.java` -- would need `CorsConfigurationSource` bean with `addExposedHeader("X-Service-Name")` if JS access needed
- Shared lib `JwtAutoConfiguration.java` -- could add global CORS bean if needed later

## Implementation Steps

### Step 1: Verify Ingress config
Review `k8s/ingress.yml` -- confirm:
- No `proxy_hide_header` directives
- No `configuration-snippet` annotations stripping response headers
- Result: **CONFIRMED** -- no changes needed

### Step 2: Verify CORS (decision: no changes)
- Network tab shows all headers -> requirement met without CORS changes
- If JS access needed later, add `Access-Control-Expose-Headers: X-Service-Name` to CORS config
- This follows YAGNI principle

## Todo List
- [ ] Verify `k8s/ingress.yml` has no header-stripping config (already confirmed in research)
- [ ] Document: no changes needed for Phase 02

## Success Criteria
- `X-Service-Name` visible in browser Network tab when accessing any API endpoint through Ingress
- No K8s Ingress config changes required
- No CORS config changes required

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| NGINX Ingress strips custom headers | Low (doesn't by default) | Test after deploy |
| Future JS needs header access | Low | Add CORS expose when needed |

## Security Considerations
- Same as Phase 01 -- header exposes service name, acceptable for dev tooling
- If needed, can restrict header to non-prod environments via K8s Ingress annotation or app property

## Next Steps
- Phase 03: Compile and test
