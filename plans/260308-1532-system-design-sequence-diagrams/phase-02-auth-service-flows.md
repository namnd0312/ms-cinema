# Phase 2: Auth Service Flows

## Context Links
- [AuthController.java](../../auth-service/src/main/java/com/namnd/springjwt/controller/AuthController.java) (~230 lines)
- [TokenValidationController.java](../../auth-service/src/main/java/com/namnd/springjwt/controller/TokenValidationController.java) (99 lines)
- [JwtService.java](../../auth-service/src/main/java/com/namnd/springjwt/service/JwtService.java) (147 lines)
- [JwtAuthenticationFilter.java](../../auth-service/src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java)
- [SecurityConfig.java](../../auth-service/src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Mermaid sequence diagrams for all auth-service REST endpoints: registration, activation, login, token refresh, forgot/reset password, logout, and validate-token.

## Key Insights from Code
- Login: AccountLockService.isLocked() check -> AuthenticationManager.authenticate() -> JwtService.generateTokenLogin() (HS512, JTI, roles+userId claims) -> RefreshTokenService.createRefreshToken() (7d, DB)
- Register: existsByEmail() check -> BCrypt encode -> save User(active=false) -> ActivationService.createActivationToken() -> EmailService.sendActivationEmail()
- Activate: ActivationService.activateAccount(token) -> verify not expired/used -> user.active=true, token.used=true
- Refresh: findByToken() -> verifyExpiration() -> generateTokenFromEmail(email, userId, roles) -> createRefreshToken() (rotation: delete old, create new)
- Forgot Password: PasswordResetService.createPasswordResetToken() -> 24h UUID token -> sendPasswordResetEmail()
- Reset Password: validate token -> BCrypt encode new password -> update user -> delete token
- Logout: extract JTI -> BlacklistedTokenService.blacklistToken(jti, expiry) via Redis SET with TTL -> deleteRefreshToken
- Validate-token: POST /api/auth/validate-token -> validateJwtToken() + isTokenBlacklisted() -> return valid/userId/email/roles (no DB hit for valid check)
- /api/users/me: JWT filter authenticates -> load User from DB -> return UserInfoResponseDto

## Diagrams to Create (7 total)

### 1. User Registration Flow
Participants: Client, API Gateway, AuthController, UserService, RegisterDtoMapper, RoleService, ActivationService, EmailService, PostgreSQL, SMTP
- POST /api/auth/register

### 2. Email Activation Flow
Participants: Client, API Gateway, AuthController, ActivationService, ActivationTokenRepository, UserService, PostgreSQL
- GET /api/auth/activate?token=uuid

### 3. User Login Flow
Participants: Client, API Gateway, AuthController, AccountLockService, AuthenticationManager, UserService, JwtService, RefreshTokenService, PostgreSQL, Redis
- POST /api/auth/login
- Include: lock check, failed attempt increment, success reset, JWT generation with roles+userId

### 4. Token Refresh Flow
Participants: Client, API Gateway, AuthController, RefreshTokenService, JwtService, PostgreSQL
- POST /api/auth/refresh-token
- Include: token rotation (delete old, create new)

### 5. Forgot & Reset Password Flow
Participants: Client, API Gateway, AuthController, PasswordResetService, UserService, EmailService, PostgreSQL, SMTP
- POST /api/auth/forgot-password -> POST /api/auth/reset-password

### 6. Logout Flow
Participants: Client, API Gateway, AuthController, JwtService, BlacklistedTokenService, RedisService, RefreshTokenService, Redis, PostgreSQL
- POST /api/auth/logout (requires Bearer token)

### 7. Token Validation Flow (microservice-to-microservice)
Participants: DownstreamService, API Gateway, TokenValidationController, JwtService, BlacklistedTokenService, Redis
- POST /api/auth/validate-token

## Source Files to Reference
- `auth-service/src/main/java/com/namnd/springjwt/controller/AuthController.java`
- `auth-service/src/main/java/com/namnd/springjwt/controller/TokenValidationController.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/JwtService.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/impl/RefreshTokenServiceImpl.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/impl/PasswordResetServiceImpl.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`
- `auth-service/src/main/java/com/namnd/springjwt/service/impl/AccountLockServiceImpl.java`

## Todo
- [ ] Registration sequence diagram
- [ ] Email activation sequence diagram
- [ ] Login sequence diagram (with lock check, failed attempts)
- [ ] Token refresh sequence diagram (with rotation)
- [ ] Forgot/reset password sequence diagram
- [ ] Logout sequence diagram (with Redis blacklist)
- [ ] Token validation sequence diagram (microservice use)
- [ ] Verify all method names match source code

## Success Criteria
- All 7 auth endpoints have sequence diagrams
- Participant names match real class names
- Alt/opt blocks for error paths (locked account, bad credentials, expired token)
- Redis interactions shown for blacklist operations
