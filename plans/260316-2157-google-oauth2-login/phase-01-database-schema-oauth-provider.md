# Phase 1: Database Schema & OAuth Provider Entity

## Context Links
- [Plan overview](./plan.md)
- [Research: Schema design](./research/researcher-02-oauth2-user-linking-schema.md)
- User entity: `auth-service/src/main/java/com/namnd/cinema/model/User.java`

## Overview
- **Priority:** P1 (blocking for all other phases)
- **Status:** pending
- **Description:** Create `UserOAuthProvider` entity/table and make `User.password` nullable for OAuth-only users

## Key Insights
- Separate table `user_oauth_providers` supports future providers (GitHub, Facebook) without schema change
- Unique constraint on (provider_name, provider_user_id) prevents duplicate links
- Unique constraint on (user_id, provider_name) prevents one user having multiple accounts from same provider
- Password=NULL for OAuth-only users; no sentinel values

## Requirements
### Functional
- New `user_oauth_providers` table with: id, user_id (FK), provider_name, provider_user_id, provider_email, linked_at
- User.password becomes nullable (OAuth-only users have no password)
- Existing users with passwords unaffected

### Non-Functional
- ddl-auto=update handles migration; no manual SQL needed
- Index on (provider_name, provider_user_id) for fast lookup

## Architecture
```
users (existing)                  user_oauth_providers (new)
+----+----------+------+         +----+---------+---------------+-------------------+
| id | email    | pwd  |   1:N   | id | user_id | provider_name | provider_user_id  |
+----+----------+------+ <-----> +----+---------+---------------+-------------------+
```

## Related Code Files

### Modify
- `auth-service/src/main/java/com/namnd/cinema/model/User.java` -- make password nullable

### Create
- `auth-service/src/main/java/com/namnd/cinema/model/UserOAuthProvider.java` -- new entity
- `auth-service/src/main/java/com/namnd/cinema/repository/UserOAuthProviderRepository.java` -- JPA repo

## Implementation Steps

### 1. Create `UserOAuthProvider` entity
File: `auth-service/src/main/java/com/namnd/cinema/model/UserOAuthProvider.java`

```java
package com.namnd.cinema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_oauth_providers",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider_name", "provider_user_id"}),
        @UniqueConstraint(columnNames = {"user_id", "provider_name"})
    })
public class UserOAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;  // "google", "github"

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;  // Google "sub" claim

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate() {
        linkedAt = LocalDateTime.now();
    }
}
```

### 2. Create `UserOAuthProviderRepository`
File: `auth-service/src/main/java/com/namnd/cinema/repository/UserOAuthProviderRepository.java`

```java
package com.namnd.cinema.repository;

import com.namnd.cinema.model.UserOAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserOAuthProviderRepository extends JpaRepository<UserOAuthProvider, Long> {

    Optional<UserOAuthProvider> findByProviderNameAndProviderUserId(
        String providerName, String providerUserId);

    boolean existsByUserIdAndProviderName(Long userId, String providerName);
}
```

### 3. Modify `User.java` -- make password nullable
Change: remove any `@Column(nullable=false)` from password (currently it has no annotation, just `private String password`). Since it's already nullable by default in JPA, just verify no DB NOT NULL constraint. The field is fine as-is; JPA String fields are nullable by default. No code change needed unless there's a NOT NULL constraint in DB.

**Verify:** password field has no `@Column(nullable=false)`. Current code: `private String password;` -- already nullable. Good.

### 4. Update `UserService` interface -- add findByUsername
No changes needed for Phase 1. The existing `findByEmail` is sufficient.

## Todo List
- [ ] Create `UserOAuthProvider.java` entity
- [ ] Create `UserOAuthProviderRepository.java`
- [ ] Verify `User.password` is nullable (no code change needed)
- [ ] Compile and verify table auto-created

## Success Criteria
- `user_oauth_providers` table created with correct columns and constraints
- Existing auth flow unaffected
- Compile succeeds

## Risk Assessment
- **Low:** ddl-auto=update might not create unique constraints on existing tables; verify after boot
- **Mitigation:** Check DB schema after first start

## Security Considerations
- No OAuth tokens (access_token, refresh_token from Google) stored -- not needed since we issue our own JWT
- Provider email stored for audit/debugging only

## Next Steps
- Phase 2: Spring Security OAuth2 configuration
