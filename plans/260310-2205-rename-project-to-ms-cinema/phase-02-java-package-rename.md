# Phase 02: Java Package Rename (com.namnd.springjwt -> com.namnd.cinema)

## Context Links
- [plan.md](plan.md)
- [Phase 01 - Maven renames](phase-01-maven-pom-renames.md)

## Overview
- **Priority:** High (core change; all imports, package statements, directory structure affected)
- **Status:** pending
- **Description:** Rename Java package `com.namnd.springjwt` to `com.namnd.cinema` across auth-service. Move directories, update all package declarations, imports, and config references.

## Key Insights
- 57 Java source files under `auth-service/src/main/java/com/namnd/springjwt/`
- 1 test file under `auth-service/src/test/java/com/namnd/springjwt/`
- Main class `SpringJwtApplication` -> `CinemaAuthApplication`
- Test class `SpringJwtApplicationTests` -> `CinemaAuthApplicationTests`
- `auth-service/pom.xml` hardcodes `<mainClass>com.namnd.springjwt.SpringJwtApplication</mainClass>`
- `auth-service/src/main/resources/logback-spring.xml` references `com.namnd.springjwt` logger name

## Requirements
- All `package com.namnd.springjwt` -> `package com.namnd.cinema`
- All `import com.namnd.springjwt` -> `import com.namnd.cinema`
- Directory `com/namnd/springjwt/` -> `com/namnd/cinema/`
- Class rename: `SpringJwtApplication` -> `CinemaAuthApplication`
- Class rename: `SpringJwtApplicationTests` -> `CinemaAuthApplicationTests`
- Update pom.xml mainClass reference
- Update logback-spring.xml logger name

## Related Code Files

### Directory Moves

| Old Path | New Path |
|----------|----------|
| `auth-service/src/main/java/com/namnd/springjwt/` | `auth-service/src/main/java/com/namnd/cinema/` |
| `auth-service/src/test/java/com/namnd/springjwt/` | `auth-service/src/test/java/com/namnd/cinema/` |

### Class Renames (file renames)

| Old File | New File |
|----------|----------|
| `.../cinema/SpringJwtApplication.java` | `.../cinema/CinemaAuthApplication.java` |
| `.../cinema/SpringJwtApplicationTests.java` | `.../cinema/CinemaAuthApplicationTests.java` |

### Files Requiring Package/Import Updates (57 main + 1 test)

**config/ (8 files)**
- `config/GlobalExceptionHandler.java`
- `config/HttpLoggingConfig.java`
- `config/MetricsConfig.java`
- `config/OpenApiConfig.java`
- `config/RedisConfig.java`
- `config/RedisKeyPrefix.java`
- `config/custom/CustomAccesDeniedHandler.java`
- `config/filter/HttpLoggingFilter.java`
- `config/filter/JwtAuthenticationFilter.java`
- `config/security/SecurityConfig.java`

**controller/ (3 files)**
- `controller/AuthController.java`
- `controller/TestController.java`
- `controller/TokenValidationController.java`

**dto/ (10 files)**
- `dto/ForgotPasswordDto.java`
- `dto/JwtResponseDto.java`
- `dto/LoginRequestDto.java`
- `dto/RefreshTokenRequestDto.java`
- `dto/RegisterDto.java`
- `dto/ResetPasswordDto.java`
- `dto/TokenRefreshResponseDto.java`
- `dto/UserInfoResponseDto.java`
- `dto/ValidateTokenRequestDto.java`
- `dto/ValidateTokenResponseDto.java`
- `dto/mapper/RegisterDtoMapper.java`

**model/ (6 files)**
- `model/ActivationToken.java`
- `model/PasswordResetToken.java`
- `model/RefreshToken.java`
- `model/Role.java`
- `model/User.java`
- `model/UserPrinciple.java`

**repository/ (5 files)**
- `repository/ActivationTokenRepository.java`
- `repository/PasswordResetTokenRepository.java`
- `repository/RefreshTokenRepository.java`
- `repository/RoleRepository.java`
- `repository/UserRepository.java`

**service/ (10 interfaces)**
- `service/AccountLockService.java`
- `service/ActivationService.java`
- `service/BlacklistedTokenService.java`
- `service/EmailService.java`
- `service/JwtService.java`
- `service/PasswordResetService.java`
- `service/RedisService.java`
- `service/RefreshTokenService.java`
- `service/RoleService.java`
- `service/UserService.java`

**service/impl/ (9 files)**
- `service/impl/AccountLockServiceImpl.java`
- `service/impl/ActivationServiceImpl.java`
- `service/impl/BlacklistedTokenServiceImpl.java`
- `service/impl/EmailServiceImpl.java`
- `service/impl/PasswordResetServiceImpl.java`
- `service/impl/RedisServiceImpl.java`
- `service/impl/RefreshTokenServiceImpl.java`
- `service/impl/RoleServiceImpl.java`
- `service/impl/UserServiceImpl.java`

**root + test (2 files)**
- `SpringJwtApplication.java` (rename to `CinemaAuthApplication.java`)
- `test/.../SpringJwtApplicationTests.java` (rename to `CinemaAuthApplicationTests.java`)

### Config Files

| File | Change |
|------|--------|
| `auth-service/pom.xml` line ~147 | `<mainClass>com.namnd.springjwt.SpringJwtApplication</mainClass>` -> `com.namnd.cinema.CinemaAuthApplication` |
| `auth-service/src/main/resources/logback-spring.xml` line 43 | `<logger name="com.namnd.springjwt"` -> `com.namnd.cinema` |

## Implementation Steps

1. **Create new directory structure:**
   ```bash
   mkdir -p auth-service/src/main/java/com/namnd/cinema
   mkdir -p auth-service/src/test/java/com/namnd/cinema
   ```

2. **Move all source files** (preserving subdirectory structure):
   ```bash
   # Move entire tree
   mv auth-service/src/main/java/com/namnd/springjwt/* auth-service/src/main/java/com/namnd/cinema/
   mv auth-service/src/test/java/com/namnd/springjwt/* auth-service/src/test/java/com/namnd/cinema/
   ```

3. **Remove old empty directories:**
   ```bash
   rmdir auth-service/src/main/java/com/namnd/springjwt
   rmdir auth-service/src/test/java/com/namnd/springjwt
   ```

4. **Bulk replace package declarations** in all 58 Java files:
   - `package com.namnd.springjwt` -> `package com.namnd.cinema` (replace_all)

5. **Bulk replace import statements** in all Java files:
   - `import com.namnd.springjwt` -> `import com.namnd.cinema` (replace_all)

6. **Rename main class file:**
   ```bash
   mv auth-service/src/main/java/com/namnd/cinema/SpringJwtApplication.java \
      auth-service/src/main/java/com/namnd/cinema/CinemaAuthApplication.java
   ```

7. **Update main class content:**
   - Class declaration: `public class SpringJwtApplication` -> `public class CinemaAuthApplication`
   - Main method: `SpringApplication.run(SpringJwtApplication.class` -> `SpringApplication.run(CinemaAuthApplication.class`

8. **Rename test class file:**
   ```bash
   mv auth-service/src/test/java/com/namnd/cinema/SpringJwtApplicationTests.java \
      auth-service/src/test/java/com/namnd/cinema/CinemaAuthApplicationTests.java
   ```

9. **Update test class content:**
   - `class SpringJwtApplicationTests` -> `class CinemaAuthApplicationTests`

10. **Update auth-service/pom.xml:**
    - `<mainClass>com.namnd.springjwt.SpringJwtApplication</mainClass>` -> `<mainClass>com.namnd.cinema.CinemaAuthApplication</mainClass>`

11. **Update logback-spring.xml:**
    - `<logger name="com.namnd.springjwt"` -> `<logger name="com.namnd.cinema"`

12. **Compile check:**
    ```bash
    mvn clean compile -pl auth-service -am
    ```

## Todo List

- [ ] Create `com/namnd/cinema/` directories (main + test)
- [ ] Move all source files from `springjwt/` to `cinema/`
- [ ] Remove old `springjwt/` directories
- [ ] Replace `package com.namnd.springjwt` in all 58 files
- [ ] Replace `import com.namnd.springjwt` in all files with cross-package imports
- [ ] Rename `SpringJwtApplication.java` -> `CinemaAuthApplication.java`
- [ ] Update class name + SpringApplication.run() in main class
- [ ] Rename `SpringJwtApplicationTests.java` -> `CinemaAuthApplicationTests.java`
- [ ] Update test class name
- [ ] Update `auth-service/pom.xml` mainClass
- [ ] Update `logback-spring.xml` logger name
- [ ] Verify `mvn clean compile -pl auth-service -am` passes

## Success Criteria
- No references to `com.namnd.springjwt` in any Java source file
- No references to `SpringJwtApplication` in any file
- `mvn clean compile` passes for auth-service and all dependent modules
- Directory `auth-service/src/main/java/com/namnd/springjwt/` no longer exists

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Missed import in a file | High | Use `grep -r "com.namnd.springjwt" auth-service/src/` after rename to verify zero matches |
| Component scan breaks | High | `@SpringBootApplication` on `CinemaAuthApplication` auto-scans `com.namnd.cinema` -- matches new package |
| IDE index cache stale | Low | Run `mvn clean` to force rebuild; IDE will re-index |
| Other modules importing auth-service classes | Medium | Only `kafka-events` is shared; it has its own package `com.namnd.cinema.events` (no -- it uses different package). Verify no cross-module imports of `com.namnd.springjwt` |
