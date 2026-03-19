# Phase 4: API Gateway Routes

## Context Links
- [Plan overview](./plan.md)
- Gateway config: `api-gateway/src/main/resources/application.yml`

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Add gateway routes for OAuth2 authorization and callback endpoints to auth-service

## Key Insights
- Gateway currently routes `/api/auth/**` to auth-service
- OAuth2 uses `/oauth2/authorization/google` (initiate) and `/login/oauth2/code/google` (callback)
- These paths don't match existing `/api/auth/**` pattern; need new routes
- No security/filtering at gateway level; just passthrough

## Requirements
### Functional
- Route `/oauth2/authorization/**` to auth-service
- Route `/login/oauth2/code/**` to auth-service

### Non-Functional
- Existing routes unaffected

## Architecture
```
Browser -> Gateway:8080/oauth2/authorization/google -> auth-service:8081/oauth2/authorization/google
Google  -> Gateway:8080/login/oauth2/code/google    -> auth-service:8081/login/oauth2/code/google
```

**Important:** Google redirect URI must point to gateway (port 8080), not directly to auth-service (8081). The `redirect-uri` in application.yml uses `{baseUrl}` which resolves based on the incoming request. Since the request comes through gateway, need to configure properly.

**Decision:** Set explicit `redirect-uri` in auth-service YAML to use gateway URL:
```yaml
redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:8080/login/oauth2/code/google}
```

## Related Code Files

### Modify
- `api-gateway/src/main/resources/application.yml` -- add 2 routes
- `auth-service/src/main/resources/application.yml` -- update redirect-uri to use gateway URL

## Implementation Steps

### 1. Add OAuth2 routes to gateway `application.yml`
Add after the `auth-service-users` route:

```yaml
- id: auth-service-oauth2-authorization
  uri: lb://auth-service
  predicates:
    - Path=/oauth2/authorization/**

- id: auth-service-oauth2-callback
  uri: lb://auth-service
  predicates:
    - Path=/login/oauth2/code/**
```

### 2. Update auth-service `redirect-uri`
In auth-service `application.yml`, change redirect-uri to explicit gateway URL:

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
            redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:8080/login/oauth2/code/google}
```

This ensures Google redirects to gateway, which forwards to auth-service.

### 3. Google Console configuration
Authorized redirect URI in Google Cloud Console must match:
- Development: `http://localhost:8080/login/oauth2/code/google`
- Production: `https://your-domain.com/login/oauth2/code/google`

## Todo List
- [ ] Add OAuth2 authorization route to gateway
- [ ] Add OAuth2 callback route to gateway
- [ ] Update auth-service redirect-uri to use gateway URL
- [ ] Verify routes work end-to-end

## Success Criteria
- `/oauth2/authorization/google` through gateway reaches auth-service and redirects to Google
- Google callback through gateway reaches auth-service callback handler
- Existing routes unaffected

## Risk Assessment
- **Medium:** `{baseUrl}` in redirect-uri might resolve to auth-service:8081 behind gateway, causing mismatch with Google's registered redirect URI
- **Mitigation:** Use explicit redirect-uri with env var pointing to gateway URL

## Security Considerations
- No additional security needed at gateway for these routes
- State parameter and PKCE handled by auth-service Spring Security

## Next Steps
- Phase 5: Testing and validation
