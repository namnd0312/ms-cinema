# Phase 01: Backend Registration Changes

## Context Links

- [Plan Overview](plan.md)
- [AuthController.java](../../auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java) (~381 lines)
- [RegisterDto.java](../../auth-service/src/main/java/com/namnd/cinema/dto/RegisterDto.java) (68 lines)
- [RegisterDtoMapper.java](../../auth-service/src/main/java/com/namnd/cinema/dto/mapper/RegisterDtoMapper.java) (28 lines)
- [User.java](../../auth-service/src/main/java/com/namnd/cinema/model/User.java) (44 lines)

## Overview

- **Priority:** P1
- **Status:** done
- Remove password from RegisterDto and registration endpoint. Make User.password explicitly nullable. Update mapper to skip encoding when password is null.

## Key Insights

- User.password (line 23) has no `nullable` annotation but is already nullable for OAuth-only users (codebase-summary line 81: "password [nullable for OAuth-only]")
- RegisterDtoMapper.toEntity() (line 25) calls `passwordEncoder.encode(dto.getPassword())` which will NPE on null
- AuthController.registerUser() (line 219) calls `passwordHistoryService.savePasswordToHistory(user1, user1.getPassword())` which should be skipped when password is null

## Requirements

### Functional
- POST /api/auth/register accepts {username, fullName, email} without password
- User created with password=null, active=false
- No password history entry on registration (deferred to activation)

### Non-Functional
- Backward compatible: existing login checks user.getPassword()==null (line 109) already returns error for OAuth-only, same applies here for unactivated users

## Architecture

No structural changes. Just field removal + null-safety in existing flow.

## Related Code Files

### Modify
- `auth-service/src/main/java/com/namnd/cinema/dto/RegisterDto.java` - Remove password field + getter/setter
- `auth-service/src/main/java/com/namnd/cinema/dto/mapper/RegisterDtoMapper.java` - Skip password encoding
- `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java` - Skip passwordHistoryService call on register
- `auth-service/src/main/java/com/namnd/cinema/model/User.java` - (Optional) add explicit nullable annotation for clarity

## Implementation Steps

### 1. Remove password from RegisterDto (RegisterDto.java)

Remove lines 13, 37-42 (password field, getter, setter). Keep all other fields.

### 2. Update RegisterDtoMapper (RegisterDtoMapper.java)

Line 25: `user.setPassword(passwordEncoder.encode(dto.getPassword()));`

Change to skip password encoding entirely:
```java
// BeanUtils.copyProperties copies username, fullName, email, roles
// Password left null - set during activation
BeanUtils.copyProperties(dto, user);
// Remove: user.setPassword(passwordEncoder.encode(dto.getPassword()));
```

Remove the `@Autowired PasswordEncoder` since no longer needed in mapper.

### 3. Update AuthController.registerUser() (AuthController.java)

Line 219: `passwordHistoryService.savePasswordToHistory(user1, user1.getPassword());`

Remove this line. Password history will be saved during activation (Phase 2).

### 4. (Optional) Explicit nullable on User.password (User.java)

Line 23: `private String password;`

No change needed since it already works as nullable for OAuth users. Optionally add comment for clarity.

## Todo List

- [x] Remove `password` field from RegisterDto.java
- [x] Remove password getter/setter from RegisterDto.java
- [x] Update RegisterDtoMapper: remove password encoding line and PasswordEncoder dependency
- [x] Remove `passwordHistoryService.savePasswordToHistory()` call from AuthController.registerUser()
- [x] Compile: `mvn -pl auth-service clean compile`
- [ ] Verify existing tests still pass

## Success Criteria

- POST /api/auth/register with {username, fullName, email} creates user with password=null
- No NPE in mapper or controller
- Compiles without errors

## Risk Assessment

- **Low:** RegisterDtoMapper still uses BeanUtils.copyProperties which copies matching fields; password field absent = skipped automatically
- **Low:** Removing PasswordEncoder from mapper; still used in AuthController for change-password

## Security Considerations

- User with password=null cannot login (blocked by active=false check, and additionally by password==null check at AuthController line 109)
- No password hash stored = no exposure risk

## Next Steps

Phase 02: Add activation-with-password endpoint to accept and hash password during activation
