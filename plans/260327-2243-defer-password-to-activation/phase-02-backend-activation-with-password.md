# Phase 02: Backend Activation with Password

## Context Links

- [Plan Overview](plan.md)
- [Phase 01](phase-01-backend-registration-changes.md)
- [ActivationService.java](../../auth-service/src/main/java/com/namnd/cinema/service/ActivationService.java) (12 lines)
- [ActivationServiceImpl.java](../../auth-service/src/main/java/com/namnd/cinema/service/impl/ActivationServiceImpl.java) (85 lines)
- [AuthController.java](../../auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java)
- [EmailServiceImpl.java](../../auth-service/src/main/java/com/namnd/cinema/service/impl/EmailServiceImpl.java) (89 lines)
- [ResetPasswordDto.java](../../auth-service/src/main/java/com/namnd/cinema/dto/ResetPasswordDto.java) - reference for DTO style
- [application.yml](../../auth-service/src/main/resources/application.yml) line 50
- [config-repo/auth-service.yml](../../config-server/src/main/resources/config-repo/auth-service.yml) line 9

## Overview

- **Priority:** P1
- **Status:** done
- Create SetupPasswordDto, add `activateWithPassword()` to ActivationService, add POST /api/auth/activate-with-password endpoint, update activation email link to point to frontend setup-password page.

## Key Insights

- Existing activateAccount() in ActivationServiceImpl (lines 56-72): validates token, sets active=true, marks token used. New method reuses same logic + adds password hashing.
- EmailServiceImpl (line 38): `activationBaseUrl + "?token=" + token` currently points to `http://localhost:8080/api/auth/activate`. Must change to frontend URL.
- PasswordHistoryService interface (line 7): `savePasswordToHistory(User user, String encodedPassword)` already exists.
- ResetPasswordDto pattern: simple @Data class with token + password fields.
- Config: `namnd.app.activationBaseUrl` in application.yml (line 50) and config-repo/auth-service.yml (line 9).

## Requirements

### Functional
- POST /api/auth/activate-with-password accepts {token, password, confirmPassword}
- Validates: password min 6 chars, password == confirmPassword, token valid & not expired & not used
- On success: hash password, set user.active=true, save password history, mark token used
- Activation email link points to frontend: `http://localhost:4200/auth/setup-password?token={uuid}`

### Non-Functional
- Server-side password validation (not just frontend)
- Token single-use (existing logic already handles this)
- @Transactional for atomicity

## Architecture

```
POST /api/auth/activate-with-password
  -> AuthController.activateWithPassword(SetupPasswordDto)
    -> ActivationService.activateWithPassword(token, password)
      -> validate token (same as activateAccount)
      -> hash password via PasswordEncoder
      -> set user.password + user.active=true
      -> save to password_history
      -> mark token used
```

## Related Code Files

### Create
- `auth-service/src/main/java/com/namnd/cinema/dto/SetupPasswordDto.java` - New DTO

### Modify
- `auth-service/src/main/java/com/namnd/cinema/service/ActivationService.java` - Add activateWithPassword method
- `auth-service/src/main/java/com/namnd/cinema/service/impl/ActivationServiceImpl.java` - Implement activateWithPassword
- `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java` - Add POST endpoint
- `auth-service/src/main/java/com/namnd/cinema/service/impl/EmailServiceImpl.java` - No code change, config change only
- `auth-service/src/main/resources/application.yml` - Update activationBaseUrl
- `config-server/src/main/resources/config-repo/auth-service.yml` - Update activationBaseUrl

## Implementation Steps

### 1. Create SetupPasswordDto

```java
package com.namnd.cinema.dto;

import lombok.Data;

@Data
public class SetupPasswordDto {
    private String token;
    private String password;
    private String confirmPassword;
}
```

### 2. Add method to ActivationService interface (line 9)

Add after `void activateAccount(String token);`:
```java
void activateWithPassword(String token, String password);
```

### 3. Implement in ActivationServiceImpl

Add PasswordEncoder and PasswordHistoryService dependencies (both already available as beans).

New method after activateAccount() (after line 72):
```java
@Override
@Transactional
public void activateWithPassword(String token, String password) {
    ActivationToken activationToken = activationTokenRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid or expired activation token."));

    if (activationToken.isUsed() || activationToken.getExpiryDate().before(new Date())) {
        throw new RuntimeException("Invalid or expired activation token.");
    }

    User user = activationToken.getUser();
    String encodedPassword = passwordEncoder.encode(password);
    user.setPassword(encodedPassword);
    user.setActive(true);
    userRepository.save(user);

    passwordHistoryService.savePasswordToHistory(user, encodedPassword);

    activationToken.setUsed(true);
    activationTokenRepository.save(activationToken);
    logger.info("Account activated with password for user: {}", user.getEmail());
}
```

### 4. Add POST endpoint to AuthController

Add after the existing GET /activate endpoint (after line 237):
```java
@Operation(summary = "Activate account and set password")
@ApiResponse(responseCode = "200", description = "Account activated with password set")
@ApiResponse(responseCode = "400", description = "Invalid token or validation error")
@PostMapping("/activate-with-password")
public ResponseEntity<?> activateWithPassword(@RequestBody SetupPasswordDto dto) {
    if (dto.getPassword() == null || dto.getPassword().length() < 6) {
        return ResponseEntity.badRequest().body("Password must be at least 6 characters.");
    }
    if (!dto.getPassword().equals(dto.getConfirmPassword())) {
        return ResponseEntity.badRequest().body("Passwords do not match.");
    }
    try {
        activationService.activateWithPassword(dto.getToken(), dto.getPassword());
        return ResponseEntity.ok("Account activated successfully! You can now login.");
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
```

### 5. Update activationBaseUrl config

**application.yml** (line 50): Change default from `http://localhost:8080/api/auth/activate` to `http://localhost:4200/auth/setup-password`

**config-repo/auth-service.yml** (line 9): Same change.

This makes email link: `http://localhost:4200/auth/setup-password?token={uuid}` which loads the frontend setup-password page.

### 6. Add /api/auth/activate-with-password to SecurityConfig permitAll

Check SecurityConfig.java to ensure `/api/auth/**` is already permitted (it is per codebase-summary). No change needed since the new endpoint matches existing wildcard.

## Todo List

- [x] Create SetupPasswordDto.java
- [x] Add `activateWithPassword(String token, String password)` to ActivationService interface
- [x] Implement activateWithPassword in ActivationServiceImpl (inject PasswordEncoder + PasswordHistoryService)
- [x] Add POST /api/auth/activate-with-password in AuthController with validation
- [x] Update activationBaseUrl in application.yml to frontend URL
- [x] Update activationBaseUrl in config-repo/auth-service.yml to frontend URL
- [x] Compile: `mvn -pl auth-service clean compile`
- [ ] Test endpoint manually or via unit test

## Success Criteria

- POST /api/auth/activate-with-password with valid token + password -> user activated, password set, history saved
- POST with invalid/expired token -> 400 error
- POST with short password or mismatch -> 400 error
- Activation email links to frontend setup-password page

## Risk Assessment

- **Low:** Existing GET /activate still works for any old activation links in emails
- **Low:** SecurityConfig already permits /api/auth/** endpoints
- **Medium:** If config-server overrides application.yml, must update both configs

## Security Considerations

- Server-side password validation (min 6 chars) independent of frontend
- Password encoded with BCrypt before storage
- Token marked as used atomically within @Transactional
- No authentication required for this endpoint (token-based auth via activation token)

## Next Steps

Phase 03: Remove password from frontend registration form
Phase 04: Create frontend setup-password page
