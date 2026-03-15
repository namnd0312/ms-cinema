---
title: "Password History Validation"
description: "Prevent reuse of 3 most recent passwords with BE validation and FE messaging"
status: complete
priority: P2
effort: 3h
branch: master
tags: [auth, security, password, backend, frontend]
created: 2026-03-15
---

# Password History Validation

Prevent users from reusing their 3 most recent passwords when changing or resetting password. Backend stores hashed password history, validates against it. Frontend provides change-password UI with error messaging.

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Backend - PasswordHistory entity & repository | complete | 30m | [phase-01](phase-01-backend-password-history-entity.md) |
| 2 | Backend - Change password endpoint & history service | complete (review fixes pending) | 1h | [phase-02](phase-02-backend-change-password-endpoint.md) |
| 3 | Frontend - Change password component & integration | complete | 1h | [phase-03](phase-03-frontend-change-password.md) |
| 4 | Testing & validation | compile/build verified; manual tests pending | 30m | [phase-04](phase-04-testing-and-validation.md) |

## Key Dependencies

- auth-service uses `ddl-auto: update` so JPA auto-creates tables
- BCryptPasswordEncoder already configured in SecurityConfig
- Error interceptor already handles 400 responses with snackbar
- Profile routes already set up at `/profile`

## Architecture

```
User changes password → POST /api/auth/change-password
  → Verify current password (AuthenticationManager or BCrypt match)
  → Check new password against top 3 PasswordHistory entries (BCrypt matches)
  → Encode new password → save to User → save to PasswordHistory

User resets password → POST /api/auth/reset-password (existing)
  → Check new password against top 3 PasswordHistory entries
  → Encode → save → save history

User registers → POST /api/auth/register (existing)
  → After save → save initial password to PasswordHistory
```

## Files Overview

**Backend (create):**
- `model/PasswordHistory.java` - JPA entity
- `repository/PasswordHistoryRepository.java` - Spring Data repo
- `service/PasswordHistoryService.java` - Interface
- `service/impl/PasswordHistoryServiceImpl.java` - Implementation
- `dto/ChangePasswordDto.java` - Request DTO

**Backend (modify):**
- `controller/AuthController.java` - Add change-password endpoint
- `service/impl/PasswordResetServiceImpl.java` - Add history check on reset
- `controller/AuthController.registerUser()` - Save initial history

**Frontend (create):**
- `features/profile/change-password/change-password.component.ts`

**Frontend (modify):**
- `core/services/auth.service.ts` - Add changePassword()
- `features/profile/profile-page/profile-page.component.ts` - Add button
- `features/profile/profile.routes.ts` - Add route
