# Phase 01 - Backend: PasswordHistory Entity & Repository

## Context Links
- [Parent Plan](plan.md)
- [Code Standards](../../docs/code-standards.md)
- Reference: [PasswordResetToken.java](../../auth-service/src/main/java/com/namnd/cinema/model/PasswordResetToken.java) (similar entity pattern)

## Overview
- **Date:** 2026-03-15
- **Priority:** P2
- **Status:** complete
- **Review:** complete — low priority: add @Index to entity (stated requirement not implemented)
- **Description:** Create PasswordHistory JPA entity and repository. Table auto-created by `ddl-auto: update`.

## Key Insights
- Follow PasswordResetToken entity pattern: @Data, @Entity, @Table, ManyToOne LAZY to User
- Use `LocalDateTime` for createdAt (project uses mix of Date and LocalDateTime; prefer modern API)
- Repository needs `findTop3ByUserOrderByCreatedAtDesc` for recent history lookup

## Requirements

### Functional
- Store password hash entries per user with timestamp
- Query top 3 most recent entries for a user

### Non-Functional
- LAZY fetch on User relationship (avoid N+1)
- Index on (user_id, created_at DESC) for query performance

## Architecture
```
password_history table:
  id BIGSERIAL PK
  user_id BIGINT FK → users(id) NOT NULL
  password_hash VARCHAR(255) NOT NULL
  created_at TIMESTAMP NOT NULL
```

## Related Code Files

### Create
- `auth-service/src/main/java/com/namnd/cinema/model/PasswordHistory.java`
- `auth-service/src/main/java/com/namnd/cinema/repository/PasswordHistoryRepository.java`

### Modify
- None

### Delete
- None

## Implementation Steps

1. Create `PasswordHistory.java` in `model/` package:
   ```java
   @Data
   @Entity
   @Table(name = "password_history")
   public class PasswordHistory {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @ManyToOne(fetch = FetchType.LAZY)
       @JoinColumn(name = "user_id", nullable = false)
       private User user;

       @Column(name = "password_hash", nullable = false)
       private String passwordHash;

       @Column(name = "created_at", nullable = false)
       private LocalDateTime createdAt;

       @PrePersist
       protected void onCreate() {
           this.createdAt = LocalDateTime.now();
       }
   }
   ```

2. Create `PasswordHistoryRepository.java` in `repository/` package:
   ```java
   public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
       List<PasswordHistory> findTop3ByUserOrderByCreatedAtDesc(User user);
   }
   ```

3. Run `mvn clean compile -pl auth-service` to verify entity compiles and JPA mapping valid.

## Todo List
- [x] Create PasswordHistory entity
- [x] Create PasswordHistoryRepository interface
- [x] Compile check
- [ ] Add @Index annotation on (user_id, created_at) — stated non-functional requirement, not yet implemented

## Success Criteria
- Entity compiles without errors
- Repository method name follows Spring Data naming convention
- Table auto-created by Hibernate on startup

## Risk Assessment
- **Low:** Table creation could fail if column name conflicts. Mitigated by using descriptive names.
- **Low:** LAZY fetch could cause LazyInitializationException outside transaction. Mitigated by always accessing within @Transactional service methods.

## Security Considerations
- Password hashes stored in DB are already BCrypt-encoded; no additional encryption needed
- No direct API exposure of this entity

## Next Steps
- Phase 02: Create PasswordHistoryService and change-password endpoint
