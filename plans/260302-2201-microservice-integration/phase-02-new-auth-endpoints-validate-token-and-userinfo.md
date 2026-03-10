# Phase 2: Add New Auth Endpoints (validate-token + userinfo)

## Context Links
- [Current AuthController](/src/main/java/com/namnd/springjwt/controller/AuthController.java) (moves to auth-service/ after Phase 1)
- [Current JwtService](/src/main/java/com/namnd/springjwt/service/JwtService.java)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 1.5h
- **Depends on:** Phase 1
- Add two endpoints for downstream microservices: `POST /api/auth/validate-token` (stateless token validation) and `GET /api/auth/userinfo` (authenticated user profile). Also embed roles in JWT claims so downstream services can authorize without DB lookup.

## Key Insights
- **Critical change:** Current `JwtService.generateTokenLogin()` and `generateTokenFromEmail()` do NOT include roles in JWT claims. Only store `subject` (email), `jti`, `issuedAt`, `expiration`. Must add `.claim("roles", roleNames)` for the starter library (Phase 3) to work.
- `validate-token` endpoint is unauthenticated (called by services that don't yet have a validated context)
- `userinfo` endpoint is authenticated (user already has valid Bearer token)
- AuthController is already 261 lines -- should extract new endpoints to separate controller to stay under 200-line limit

## Requirements

### Functional
- `POST /api/auth/validate-token` -- accepts `{ "token": "..." }`, returns `{ "valid": true, "userId": 1, "email": "...", "roles": ["ROLE_USER"] }` or `{ "valid": false }`
- `GET /api/auth/userinfo` -- requires Bearer token, returns `{ "id": 1, "email": "...", "username": "...", "fullName": "...", "roles": ["ROLE_USER"] }`
- Roles embedded in JWT claims: `roles` claim as `List<String>`

### Non-functional
- validate-token must NOT hit database when token is valid (parse claims only); only check blacklist via Redis
- userinfo fetches fresh data from DB (returns current user state)

## Architecture

### Token Claims (after change)
```json
{
  "sub": "user@email.com",
  "jti": "uuid",
  "iat": 1234567890,
  "exp": 1234568790,
  "roles": ["ROLE_USER", "ROLE_ADMIN"],
  "userId": 1
}
```

### Endpoint Flow
```
POST /api/auth/validate-token
  -> parse JWT claims (no DB)
  -> check blacklist by JTI (Redis)
  -> return validity + claims

GET /api/auth/userinfo
  -> JwtAuthenticationFilter validates Bearer token
  -> controller gets authenticated principal
  -> fetch User from DB by email
  -> return user profile DTO
```

## Related Code Files

### Files to Modify
- `auth-service/src/main/java/com/namnd/springjwt/service/JwtService.java`
  - Add `roles` and `userId` claims to `generateTokenLogin()`
  - Add `roles` and `userId` claims to `generateTokenFromEmail()`
  - Add `getRolesFromToken(String token)` method
  - Add `getUserIdFromToken(String token)` method
- `auth-service/src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java`
  - Verify `/api/auth/validate-token` is already covered by `/api/auth/**` permitAll (it is)

### Files to Create
- `auth-service/src/main/java/com/namnd/springjwt/controller/TokenValidationController.java` -- new controller for validate-token + userinfo
- `auth-service/src/main/java/com/namnd/springjwt/dto/ValidateTokenRequestDto.java`
- `auth-service/src/main/java/com/namnd/springjwt/dto/ValidateTokenResponseDto.java`
- `auth-service/src/main/java/com/namnd/springjwt/dto/UserInfoResponseDto.java`

## Implementation Steps

### 1. Modify JwtService -- embed roles + userId in token claims

In `generateTokenLogin()`:
```java
public String generateTokenLogin(Authentication authentication) {
    UserPrinciple userPrinciple = (UserPrinciple) authentication.getPrincipal();
    List<String> roles = userPrinciple.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

    return Jwts.builder()
            .subject(userPrinciple.getUsername())  // email
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .claim("userId", userPrinciple.getId())  // need to add getId() -- it exists via field
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
            .signWith(getSigningKey())
            .compact();
}
```

Note: `UserPrinciple` has `id` field but no public getter. Need to add `public Long getId() { return id; }`.

In `generateTokenFromEmail()` -- this method doesn't have roles context. Two options:
- (a) Look up user from DB (adds dependency on UserService -- circular risk)
- (b) Add overload `generateTokenFromEmail(String email, Long userId, List<String> roles)`

**Decision:** Option (b). Add overloaded method. Update `refreshToken` endpoint in AuthController to pass roles.

Add extraction methods:
```java
@SuppressWarnings("unchecked")
public List<String> getRolesFromToken(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build()
            .parseSignedClaims(token).getPayload()
            .get("roles", List.class);
}

public Long getUserIdFromToken(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build()
            .parseSignedClaims(token).getPayload()
            .get("userId", Long.class);
}
```

### 2. Add getId() to UserPrinciple (if missing as public method)
UserPrinciple has `private Long id` but no getter. Add:
```java
public Long getId() { return id; }
```

### 3. Create DTOs

**ValidateTokenRequestDto.java:**
```java
@Data
public class ValidateTokenRequestDto {
    @NotBlank
    private String token;
}
```

**ValidateTokenResponseDto.java:**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidateTokenResponseDto {
    private boolean valid;
    private Long userId;
    private String email;
    private List<String> roles;
}
```

**UserInfoResponseDto.java:**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserInfoResponseDto {
    private Long id;
    private String email;
    private String username;
    private String fullName;
    private List<String> roles;
}
```

### 4. Create TokenValidationController

```java
@RestController
@RequestMapping("/api/auth")
public class TokenValidationController {

    @Autowired private JwtService jwtService;
    @Autowired private BlacklistedTokenService blacklistedTokenService;
    @Autowired private UserService userService;

    @PostMapping("/validate-token")
    public ResponseEntity<ValidateTokenResponseDto> validateToken(
            @Valid @RequestBody ValidateTokenRequestDto request) {
        try {
            String token = request.getToken();
            if (!jwtService.validateJwtToken(token)) {
                return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                        .valid(false).build());
            }
            // Check blacklist
            String jti = jwtService.getJtiFromToken(token);
            if (jti != null && blacklistedTokenService.isTokenBlacklisted(jti)) {
                return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                        .valid(false).build());
            }
            return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                    .valid(true)
                    .userId(jwtService.getUserIdFromToken(token))
                    .email(jwtService.getEmailFromJwtToken(token))
                    .roles(jwtService.getRolesFromToken(token))
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(ValidateTokenResponseDto.builder()
                    .valid(false).build());
        }
    }

    @GetMapping("/userinfo")
    public ResponseEntity<UserInfoResponseDto> getUserInfo(
            @AuthenticationPrincipal UserPrinciple userPrinciple) {
        // This endpoint requires authentication (not under permitAll pattern)
        // Note: /api/auth/** is permitAll, so must either:
        // (a) change this path to /api/user/info, or
        // (b) move userinfo out of /api/auth/
        // Decision: use /api/users/me instead
        // ... see Security Config note below
    }
}
```

**Important path decision:** `/api/auth/**` is `permitAll`. The `userinfo` endpoint requires authentication. Options:
1. Map to `GET /api/users/me` (outside permitAll pattern) -- **recommended**
2. Add explicit authenticated matcher before the wildcard

**Decision:** Use `GET /api/users/me` for userinfo. Keep controller name as `TokenValidationController` but map userinfo under `/api/users`.

### 5. Update SecurityConfig
No change needed for validate-token (covered by `/api/auth/**` permitAll).
`/api/users/me` requires authentication (already covered by `.anyRequest().authenticated()`).

### 6. Update AuthController.refreshToken()
Pass userId and roles to `generateTokenFromEmail()` overload:
```java
String newAccessToken = jwtService.generateTokenFromEmail(
    user.getEmail(),
    user.getId(),
    user.getRoles().stream().map(Role::getName).toList()
);
```

### 7. Compile and test
```bash
cd auth-service && mvn compile
```

## Todo List
- [ ] Add `getId()` getter to UserPrinciple (if not already public)
- [ ] Modify `JwtService.generateTokenLogin()` -- add `roles` and `userId` claims
- [ ] Add `JwtService.generateTokenFromEmail(email, userId, roles)` overload
- [ ] Add `JwtService.getRolesFromToken()` and `getUserIdFromToken()` methods
- [ ] Create `ValidateTokenRequestDto`
- [ ] Create `ValidateTokenResponseDto`
- [ ] Create `UserInfoResponseDto`
- [ ] Create `TokenValidationController` with validate-token and userinfo endpoints
- [ ] Update `AuthController.refreshToken()` to pass roles/userId to token generation
- [ ] Verify SecurityConfig permits validate-token, authenticates userinfo
- [ ] Compile and run existing tests

## Success Criteria
- `POST /api/auth/validate-token` returns valid=true with claims for a good token
- `POST /api/auth/validate-token` returns valid=false for expired/invalid/blacklisted token
- `GET /api/users/me` returns user profile when authenticated
- `GET /api/users/me` returns 401 when unauthenticated
- Existing login/register/refresh/logout endpoints still work
- JWT tokens now contain `roles` and `userId` claims

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Existing tokens (without roles claim) break after deploy | Medium | getRolesFromToken returns null/empty for old tokens; starter handles gracefully |
| generateTokenFromEmail overload breaks refresh flow | Low | Test refresh-token endpoint explicitly |
| UserPrinciple.getId() not accessible from JwtService | Low | Add public getter |

## Security Considerations
- `validate-token` is unauthenticated by design (services call it to validate tokens they received)
- validate-token only returns claims already in the JWT (no DB data leak)
- `userinfo` requires authentication -- returns only the authenticated user's own data
- Rate limit validate-token in production (gateway-level, Phase 5)

## Next Steps
- Phase 3: JWT starter library uses the same claim extraction logic
- Phase 4: Spring Cloud integration
