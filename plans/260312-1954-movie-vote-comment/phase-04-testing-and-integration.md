# Phase 4: Testing & Integration

## Context Links

- [Plan Overview](plan.md)
- [Phase 1: Entities](phase-01-entities-and-repositories.md)
- [Phase 2: Services](phase-02-dtos-and-services.md)
- [Phase 3: Controllers](phase-03-controllers-and-security.md)
- API Gateway config: `api-gateway/src/main/resources/application.yml`
- Test command: `mvn -pl movie-service test`

## Overview

- **Priority:** P1
- **Status:** pending
- **Description:** Write unit tests for services, integration tests for controllers, verify gateway routing, and run full compile/test cycle.

## Key Insights

- No existing tests in movie-service (`src/test/` is empty); we establish test patterns here
- Use `@SpringBootTest` + `@AutoConfigureMockMvc` for controller integration tests
- Use `@DataJpaTest` for repository tests (embedded H2 or Testcontainers PostgreSQL)
- Service unit tests: mock repositories with `@ExtendWith(MockitoExtension.class)`
- No mocking in integration tests per project rules; use real DB via Testcontainers or H2
- JWT auth in tests: use `@WithMockUser` or inject test SecurityContext

## Requirements

### Functional
- Service unit tests: verify business logic (upsert, ownership, toggle)
- Repository tests: verify custom `@Query` methods
- Controller integration tests: verify endpoints, auth, error responses

### Non-Functional
- Test files under 200 lines each; split by domain
- AAA pattern (Arrange/Act/Assert) per code standards
- Descriptive test names: `methodName_scenario_expectedResult`

## Architecture

```
src/test/java/com/namnd/movieservice/
├── service/
│   ├── MovieRatingServiceTest.java      # Unit tests (Mockito)
│   ├── MovieCommentServiceTest.java     # Unit tests (Mockito)
│   └── CommentReactionServiceTest.java  # Unit tests (Mockito)
├── controller/
│   ├── MovieRatingControllerTest.java   # Integration (MockMvc)
│   ├── MovieCommentControllerTest.java  # Integration (MockMvc)
│   └── CommentReactionControllerTest.java # Integration (MockMvc)
└── repository/
    ├── MovieRatingRepositoryTest.java   # @DataJpaTest
    └── CommentReactionRepositoryTest.java # @DataJpaTest
```

## Related Code Files

### Files to Create
- `movie-service/src/test/java/com/namnd/movieservice/service/MovieRatingServiceTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/service/MovieCommentServiceTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/service/CommentReactionServiceTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/controller/MovieRatingControllerTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/controller/MovieCommentControllerTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/controller/CommentReactionControllerTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/repository/MovieRatingRepositoryTest.java`
- `movie-service/src/test/java/com/namnd/movieservice/repository/CommentReactionRepositoryTest.java`

### Files to Possibly Modify
- `movie-service/pom.xml` - add test dependencies (H2, spring-security-test) if not present
- `api-gateway/src/main/resources/application.yml` - add `/api/comments/**` route to movie-service

## Implementation Steps

### Step 1: Verify/add test dependencies in pom.xml

Ensure these exist (most should be inherited from spring-boot-starter-test):
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Add `src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
```

### Step 2: Write MovieRatingService unit tests

Key test cases:
- `createOrUpdateRating_newRating_createsSuccessfully` - no existing rating, creates new
- `createOrUpdateRating_existingRating_updatesRating` - existing rating for same movie+user, updates value
- `createOrUpdateRating_movieNotFound_throwsEntityNotFoundException`
- `getRatingSummary_withRatings_returnsAverageAndCount`
- `getRatingSummary_noRatings_returnsZeros`
- `getRatingSummary_withAuthUser_includesUserRating`

```java
@ExtendWith(MockitoExtension.class)
class MovieRatingServiceTest {
    @Mock private MovieRatingRepository ratingRepository;
    @Mock private MovieRepository movieRepository;
    @InjectMocks private MovieRatingServiceImpl ratingService;

    @Test
    void createOrUpdateRating_newRating_createsSuccessfully() {
        // Arrange: mock movieRepository.findById returns movie, ratingRepo returns empty
        // Act: call createOrUpdateRating
        // Assert: verify ratingRepository.save called with new entity, rating=4
    }
}
```

### Step 3: Write MovieCommentService unit tests

Key test cases:
- `createComment_validInput_createsSuccessfully`
- `updateComment_ownerUpdates_updatesContent`
- `updateComment_nonOwner_throwsAccessDeniedException`
- `deleteComment_ownerDeletes_setsStatusDeleted`
- `deleteComment_nonOwner_throwsAccessDeniedException`
- `getCommentsByMovie_returnsPaginatedActiveComments`

### Step 4: Write CommentReactionService unit tests

Key test cases:
- `toggleReaction_noExisting_createsNewReaction`
- `toggleReaction_sameType_removesReaction` (toggle off)
- `toggleReaction_differentType_switchesReaction` (like to dislike)
- `removeReaction_existing_deletesReaction`
- `removeReaction_noExisting_throwsEntityNotFoundException`

### Step 5: Write repository tests

**MovieRatingRepositoryTest** (`@DataJpaTest`):
- `findAverageRatingByMovieId_multipleRatings_returnsCorrectAverage`
- `findByMovieIdAndUserId_exists_returnsRating`
- `countByMovieId_multipleRatings_returnsCount`

**CommentReactionRepositoryTest** (`@DataJpaTest`):
- `countLikesByCommentId_mixedReactions_returnsOnlyLikes`
- `countDislikesByCommentId_mixedReactions_returnsOnlyDislikes`
- `findByCommentIdAndUserId_uniqueConstraint_enforcedByDb`

### Step 6: Write controller integration tests

Use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`.

**Auth setup for tests:**
```java
// For authenticated endpoints - create mock JWT auth
private static SecurityContext mockAuth(Long userId) {
    JwtAuthenticatedUser principal = new JwtAuthenticatedUser(userId, "test@test.com", List.of("ROLE_USER"));
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    return ctx;
}
```

**MovieRatingControllerTest** key cases:
- `rate_authenticated_returns200` - POST with valid rating
- `rate_unauthenticated_returns401`
- `rate_invalidRating_returns400` - rating=6
- `getSummary_public_returns200` - GET without auth

**MovieCommentControllerTest** key cases:
- `create_authenticated_returns201`
- `list_public_returns200WithPagination`
- `update_owner_returns200`
- `update_nonOwner_returns403`
- `delete_owner_returns204`

**CommentReactionControllerTest** key cases:
- `toggle_authenticated_returns200`
- `toggle_unauthenticated_returns401`
- `remove_authenticated_returns200`

### Step 7: Verify API Gateway routing

Check if `/api/comments/**` route exists in gateway config. If not, add:
```yaml
- id: movie-service-comments
  uri: lb://movie-service
  predicates:
    - Path=/api/comments/**
```

### Step 8: Full build verification
```bash
cd /path/to/ms-cinema
mvn -pl movie-service clean compile    # compile check
mvn -pl movie-service test             # run all tests
```

## Todo List

- [ ] Add H2 + spring-security-test dependencies to pom.xml (if missing)
- [ ] Create `application-test.yml` with H2 in-memory config
- [ ] Write `MovieRatingServiceTest` (6 test cases)
- [ ] Write `MovieCommentServiceTest` (6 test cases)
- [ ] Write `CommentReactionServiceTest` (5 test cases)
- [ ] Write `MovieRatingRepositoryTest` (3 test cases)
- [ ] Write `CommentReactionRepositoryTest` (3 test cases)
- [ ] Write `MovieRatingControllerTest` (4 test cases)
- [ ] Write `MovieCommentControllerTest` (5 test cases)
- [ ] Write `CommentReactionControllerTest` (3 test cases)
- [ ] Verify/update API Gateway routes for `/api/comments/**`
- [ ] Run `mvn -pl movie-service clean compile` - passes
- [ ] Run `mvn -pl movie-service test` - all tests pass

## Success Criteria

- All 35 test cases pass
- No mocking in integration tests (use real H2 DB)
- Service unit tests mock only repositories (Mockito)
- Controller tests verify HTTP status, response body, and auth enforcement
- Repository tests verify custom `@Query` methods return correct aggregations
- `mvn -pl movie-service test` exits 0

## Risk Assessment

- **H2 vs PostgreSQL differences**: H2 handles most JPA queries identically; `@Query` JPQL is DB-agnostic. Risk: TEXT column type may differ; mitigate with `columnDefinition` in test profile
- **JWT filter in integration tests**: may intercept requests; use `@MockBean JwtAuthenticationFilter` or configure test security context directly
- **No existing test infrastructure**: first tests in movie-service; may uncover missing test config

## Security Considerations

- Test auth bypass uses `SecurityContextHolder` mock, not real JWT tokens
- No real credentials in test config
- H2 in-memory DB disposed after test; no persistent test data

## Next Steps

- After all tests pass, run `mvn clean compile` at root level to verify no cross-module breakage
- Update API documentation in `docs/api-documentation.md` with new endpoints
- Update `docs/project-roadmap.md` and `docs/project-changelog.md`
