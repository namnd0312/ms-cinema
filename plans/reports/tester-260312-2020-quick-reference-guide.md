# Phase 4 Testing - Quick Reference Guide

**Status:** ✓ COMPLETE — All 18 Tests Pass

---

## Test Execution Command

```bash
# Run all movie-service tests
mvn -pl movie-service test

# Run specific test class
mvn -pl movie-service test -Dtest=MovieRatingServiceTest
mvn -pl movie-service test -Dtest=MovieCommentServiceTest
mvn -pl movie-service test -Dtest=CommentReactionServiceTest

# Run all tests with clean build
mvn -pl movie-service clean test
```

---

## Test Classes Summary

| Class | Tests | Status | Coverage |
|-------|-------|--------|----------|
| MovieRatingServiceTest | 6 | ✓ PASS | Upsert, aggregation, error handling |
| MovieCommentServiceTest | 7 | ✓ PASS | CRUD, ownership, soft-delete, pagination |
| CommentReactionServiceTest | 5 | ✓ PASS | Toggle state machine, aggregation |

---

## Key Test Scenarios

### MovieRatingServiceTest (6 tests)
1. **New Rating Creation** — Create rating when none exists
2. **Existing Rating Update** — Update rating in upsert operation
3. **Movie Not Found** — EntityNotFoundException for missing movie
4. **Summary with Ratings** — Aggregate average and count
5. **Summary No Ratings** — Return zeros for empty collection
6. **User-Specific Lookup** — Include user's rating in summary

### MovieCommentServiceTest (7 tests)
1. **Create Comment** — New comment on movie
2. **Owner Update** — Update by owner with reaction enrichment
3. **Non-Owner Update Denied** — AccessDeniedException
4. **Owner Delete** — Soft-delete by owner
5. **Admin Delete** — Admin bypass soft-delete
6. **Non-Owner Delete Denied** — AccessDeniedException
7. **Paginated Retrieval** — Active comments only, with reactions

### CommentReactionServiceTest (5 tests)
1. **Create Reaction** — New like/dislike on comment
2. **Toggle Same Type** — Remove reaction on same-type click
3. **Switch Type** — Change from like to dislike or vice versa
4. **Summary with User Reaction** — Counts + user's current reaction
5. **Deleted Comment** — EntityNotFoundException

---

## Test Structure (AAA Pattern)

All tests follow Arrange/Act/Assert pattern:

```java
@Test
void methodName_scenario_expectedResult() {
    // Arrange: Setup mocks and test data
    MovieRating rating = new MovieRating();
    when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));

    // Act: Call method under test
    MovieRatingDto result = ratingService.createOrUpdateRating(MOVIE_ID, USER_ID, request);

    // Assert: Verify outcome and mock calls
    assertNotNull(result);
    assertEquals(5, result.rating());
    verify(ratingRepository).save(any(MovieRating.class));
}
```

---

## Mock Strategy

**Mocked Components:**
- MovieRatingRepository
- MovieCommentRepository
- CommentReactionRepository
- MovieRepository

**Not Mocked:**
- Services under test (using @InjectMocks)
- DTOs and model objects (real instances)

**Verification:**
- `verify(repository).save(...)` — Confirm persistence calls
- `verify(repository, never()).delete(...)` — Confirm no delete
- `when(...).thenReturn(...)` — Setup mock responses
- Multiple returns for sequential calls

---

## Common Test Constants

```java
private static final Long MOVIE_ID = 1L;
private static final Long COMMENT_ID = 10L;
private static final Long USER_ID = 100L;
private static final Long OTHER_USER_ID = 101L;
```

---

## Error Scenarios Tested

| Scenario | Exception | Test |
|----------|-----------|------|
| Movie not found | EntityNotFoundException | createOrUpdateRating_movieNotFound |
| Non-owner update | AccessDeniedException | updateComment_nonOwner |
| Non-owner delete | AccessDeniedException | deleteComment_nonOwner |
| Deleted comment reaction | EntityNotFoundException | toggleReaction_deletedComment |

---

## Build Configuration

**New Dependencies (pom.xml):**
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

**Test Configuration (application-test.yml):**
- Database: H2 in-memory (jdbc:h2:mem:testdb)
- JPA: Hibernate DDL auto=create-drop
- Kafka: Disabled
- Eureka: Disabled
- Config Server: Disabled

---

## File Locations

**Test Source:**
```
movie-service/src/test/java/com/namnd/movieservice/service/
├── MovieRatingServiceTest.java (176 lines)
├── MovieCommentServiceTest.java (256 lines)
└── CommentReactionServiceTest.java (199 lines)
```

**Test Configuration:**
```
movie-service/src/test/resources/
└── application-test.yml
```

**Reports:**
```
plans/reports/
├── tester-260312-2023-movie-voting-comments-phase-4.md (detailed)
├── tester-260312-2020-phase-4-completion-summary.md (overview)
└── tester-260312-2020-quick-reference-guide.md (this file)
```

---

## Troubleshooting

### Test Fails: "Cannot invoke Movie.getId()"
**Cause:** MovieComment.movie field not set in test
**Fix:** Add `comment.setMovie(movie)` before using comment in test

### Test Fails: "Wanted but not invoked"
**Cause:** Mock return behavior doesn't match service logic
**Fix:** Use `thenReturn(...).thenReturn(...)` for sequential calls

### Build Fails: "H2 dependency not found"
**Cause:** pom.xml not updated
**Fix:** Add h2 and spring-security-test dependencies to pom.xml

### Slow Tests (>50ms)
**Cause:** Unnecessary mocking or setup
**Fix:** Minimize mock configuration, use constants

---

## Test Maintenance

**When Adding New Service Methods:**
1. Create new test case in appropriate test class
2. Follow naming pattern: `methodName_scenario_expectedResult`
3. Use AAA pattern (Arrange/Act/Assert)
4. Mock only repositories (@Mock)
5. Verify service calls and return values
6. Run: `mvn -pl movie-service test`

**When Modifying Service Logic:**
1. Run tests to identify failures
2. Fix service or test logic as appropriate
3. Never skip failing tests
4. Keep test execution <1 second total

---

## Next Steps

1. **Code Review:** Delegate to code-reviewer agent
2. **Integration Tests:** Add controller endpoint tests with MockMvc
3. **Coverage Report:** Add JaCoCo plugin for code coverage metrics
4. **Documentation:** Update API documentation with test scenarios

---

**Last Updated:** 2026-03-12T20:24:00+07:00
**Service:** movie-service v0.0.1-SNAPSHOT
**Test Framework:** JUnit 5 + Mockito
**Status:** ✓ READY FOR PRODUCTION
