# Phase 02 - Backend: Change Password Endpoint & History Service

## Context Links
- [Parent Plan](plan.md)
- [Phase 01](phase-01-backend-password-history-entity.md) (dependency)
- [AuthController.java](../../auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java)
- [PasswordResetServiceImpl.java](../../auth-service/src/main/java/com/namnd/cinema/service/impl/PasswordResetServiceImpl.java)

## Overview
- **Date:** 2026-03-15
- **Priority:** P2
- **Status:** complete (review fixes pending)
- **Review:** complete — HIGH: add @Transactional to changePassword(); HIGH: add history pruning in savePasswordToHistory()
- **Description:** Create PasswordHistoryService, ChangePasswordDto, add POST /api/auth/change-password endpoint. Modify reset-password and register flows to integrate history.

## Key Insights
- AuthController uses @Autowired field injection (existing pattern, follow it for consistency)
- PasswordResetServiceImpl.resetPassword() must also check history before saving
- Registration must seed initial password to history so first change validates correctly
- SecurityContextHolder provides authenticated user email in change-password flow

## Requirements

### Functional
- POST /api/auth/change-password: verify current password, check history (3 most recent), save new
- Reset-password: check history before saving
- Register: save initial password to history after user creation
- Return clear error messages for each failure case

### Non-Functional
- All operations @Transactional
- BCrypt.matches() for comparing raw password against stored hashes (expensive but necessary)

## Architecture
```
POST /api/auth/change-password
  ├── Extract email from SecurityContext
  ├── Load User by email
  ├── Verify currentPassword matches user.getPassword() (BCrypt)
  ├── Validate newPassword == confirmPassword
  ├── PasswordHistoryService.isPasswordReused(user, newPassword)
  │   └── findTop3 → BCrypt.matches() each
  ├── Encode newPassword
  ├── user.setPassword(encoded) → save
  └── PasswordHistoryService.savePasswordToHistory(user, encoded)
```

## Related Code Files

### Create
- `auth-service/src/main/java/com/namnd/cinema/dto/ChangePasswordDto.java`
- `auth-service/src/main/java/com/namnd/cinema/service/PasswordHistoryService.java`
- `auth-service/src/main/java/com/namnd/cinema/service/impl/PasswordHistoryServiceImpl.java`

### Modify
- `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java` - Add change-password endpoint + inject PasswordHistoryService + seed history on register
- `auth-service/src/main/java/com/namnd/cinema/service/impl/PasswordResetServiceImpl.java` - Add history check in resetPassword()

### Delete
- None

## Implementation Steps

### Step 1: Create ChangePasswordDto
```java
package com.namnd.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
```

### Step 2: Create PasswordHistoryService interface
```java
package com.namnd.cinema.service;

import com.namnd.cinema.model.User;

public interface PasswordHistoryService {
    boolean isPasswordReused(User user, String rawNewPassword);
    void savePasswordToHistory(User user, String encodedPassword);
}
```

### Step 3: Create PasswordHistoryServiceImpl
```java
package com.namnd.cinema.service.impl;

@Service
public class PasswordHistoryServiceImpl implements PasswordHistoryService {
    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public boolean isPasswordReused(User user, String rawNewPassword) {
        List<PasswordHistory> recentPasswords =
            passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        return recentPasswords.stream()
            .anyMatch(ph -> passwordEncoder.matches(rawNewPassword, ph.getPasswordHash()));
    }

    @Override
    @Transactional
    public void savePasswordToHistory(User user, String encodedPassword) {
        PasswordHistory history = new PasswordHistory();
        history.setUser(user);
        history.setPasswordHash(encodedPassword);
        passwordHistoryRepository.save(history);
    }
}
```

### Step 4: Add change-password endpoint to AuthController

Add field:
```java
@Autowired
private PasswordHistoryService passwordHistoryService;
```

Add endpoint method (~35 lines):
```java
@Operation(summary = "Change password for authenticated user")
@ApiResponse(responseCode = "200", description = "Password changed successfully")
@ApiResponse(responseCode = "400", description = "Validation error")
@SecurityRequirement(name = "bearerAuth")
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
    // Get authenticated user
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    User user = userService.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // Verify current password
    if (!encoder.matches(dto.getCurrentPassword(), user.getPassword())) {
        return ResponseEntity.badRequest().body("Current password is incorrect");
    }

    // Verify new password matches confirm
    if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
        return ResponseEntity.badRequest().body("New password and confirm password do not match");
    }

    // Check password history
    if (passwordHistoryService.isPasswordReused(user, dto.getNewPassword())) {
        return ResponseEntity.badRequest()
            .body("Cannot reuse your 3 most recent passwords");
    }

    // Encode and save
    String encoded = encoder.encode(dto.getNewPassword());
    user.setPassword(encoded);
    userService.save(user);
    passwordHistoryService.savePasswordToHistory(user, encoded);

    return ResponseEntity.ok("Password changed successfully");
}
```

### Step 5: Modify registerUser() in AuthController

After `userService.save(user1)` line, add:
```java
passwordHistoryService.savePasswordToHistory(user1, user1.getPassword());
```
This seeds initial password so the first change validates correctly.

### Step 6: Modify PasswordResetServiceImpl.resetPassword()

Inject PasswordHistoryService:
```java
@Autowired
private PasswordHistoryService passwordHistoryService;
```

Before `user.setPassword(passwordEncoder.encode(newPassword))`, add:
```java
if (passwordHistoryService.isPasswordReused(user, newPassword)) {
    throw new RuntimeException("Cannot reuse your 3 most recent passwords");
}
```

After saving user, add:
```java
passwordHistoryService.savePasswordToHistory(user, user.getPassword());
```

### Step 7: Compile check
```bash
mvn clean compile -pl auth-service
```

## Todo List
- [x] Create ChangePasswordDto
- [x] Create PasswordHistoryService interface
- [x] Create PasswordHistoryServiceImpl
- [x] Add change-password endpoint to AuthController
- [x] Seed initial password history on registration
- [x] Add history check to resetPassword()
- [x] Save history after resetPassword()
- [x] Compile check
- [ ] Add @Transactional to AuthController.changePassword() — HIGH priority fix
- [ ] Add history pruning (keep only 3 most recent) in savePasswordToHistory() — HIGH priority fix
- [ ] Add @Size(min=6) to confirmPassword in ChangePasswordDto — medium priority

## Success Criteria
- POST /api/auth/change-password returns 200 on valid change
- Returns 400 with "Current password is incorrect" for wrong current password
- Returns 400 with "Cannot reuse your 3 most recent passwords" for reused password
- Returns 400 with "New password and confirm password do not match" for mismatch
- Reset-password also checks history
- New user registration seeds initial history entry

## Risk Assessment
- **Medium:** BCrypt.matches() on 3 hashes is CPU-expensive (~100ms each). Acceptable for password change (infrequent operation).
- **Low:** Race condition if user changes password twice simultaneously. Mitigated by @Transactional.

## Security Considerations
- Never log passwords (raw or encoded)
- Current password required for change-password (prevents unauthorized changes if session hijacked)
- Rate limiting not added here (could be future enhancement)

## Next Steps
- Phase 03: Frontend change-password component
