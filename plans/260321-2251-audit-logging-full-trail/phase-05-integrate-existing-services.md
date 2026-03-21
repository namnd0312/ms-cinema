# Phase 5: Integrate Existing Services

## Context Links
- [Plan overview](plan.md)
- [Phase 2 — interceptor library](phase-02-audit-interceptor-library.md)
- Existing services: auth-service, movie-service, booking-service, payment-service

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- Add @Auditable annotations to key business methods, register AuditEntityListener on important entities, add kafka-events audit auto-config dependency

## Key Insights
- Only audit **write operations** (create, update, delete) and **auth events** (login, logout) — not reads (YAGNI)
- Each service already depends on kafka-events — auto-config activates automatically once Kafka is on classpath
- Focus on high-value audit points: user auth, booking creation, payment processing, movie/showtime CRUD
- EntityListener for entities where data change tracking matters: User, Movie, Booking, Payment

## Requirements

### Functional
- auth-service: audit login, logout, register, password change, role changes
- movie-service: audit movie CRUD, showtime CRUD
- booking-service: audit booking creation, cancellation, status changes
- payment-service: audit payment initiation, completion, failure

### Non-Functional
- Zero downtime — additive annotations only
- Existing business logic unchanged
- Audit failures must not break business operations (after-commit pattern)

## Architecture

Each service gets:
1. `@Auditable` on service-layer methods
2. `@EntityListeners(AuditEntityListener.class)` on key JPA entities
3. Kafka producer config for `audit-events` topic (auto-configured)

## Related Code Files

### Modify — auth-service
- `auth-service/src/main/java/com/namnd/cinema/service/impl/UserServiceImpl.java` — @Auditable on register, updateProfile
- `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java` — @Auditable on login, logout, changePassword
- `auth-service/src/main/java/com/namnd/cinema/domain/User.java` (or equivalent entity) — @EntityListeners

### Modify — movie-service
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java` — @Auditable on create, update, delete
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/ShowtimeServiceImpl.java` — @Auditable on create, update, delete
- `movie-service/src/main/java/com/namnd/movieservice/domain/Movie.java` — @EntityListeners
- `movie-service/src/main/java/com/namnd/movieservice/domain/Showtime.java` — @EntityListeners

### Modify — booking-service
- `booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingServiceImpl.java` — @Auditable on create, cancel
- `booking-service/src/main/java/com/namnd/bookingservice/domain/Booking.java` — @EntityListeners

### Modify — payment-service
- `payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java` — @Auditable on create, processWebhook
- `payment-service/src/main/java/com/namnd/paymentservice/domain/Payment.java` — @EntityListeners

### Modify — Kafka producer config (if not auto-configured)
- Each service may need `spring.kafka.producer` config for JSON serializer if not already present

## Implementation Steps

1. **Verify Kafka producer config** in each service:
   - Check if `spring.kafka.producer.value-serializer=JsonSerializer` exists
   - If not, add to service's application.yml or config-server config
   - Most services already have Kafka producer config (payment, booking, notification produce events)

2. **auth-service integration:**
   ```java
   // AuthController or AuthServiceImpl
   @Auditable(action = AuditAction.LOGIN, entityType = "User")
   public AuthResponse login(LoginRequest request) { ... }

   @Auditable(action = AuditAction.LOGOUT, entityType = "User")
   public void logout(String token) { ... }

   @Auditable(action = AuditAction.CREATE, entityType = "User")
   public void register(RegisterRequest request) { ... }

   @Auditable(action = AuditAction.UPDATE, entityType = "User")
   public void changePassword(ChangePasswordRequest request) { ... }
   ```
   ```java
   @Entity
   @EntityListeners(AuditEntityListener.class)
   public class User { ... }
   ```

3. **movie-service integration:**
   ```java
   @Auditable(action = AuditAction.CREATE, entityType = "Movie")
   public MovieResponse createMovie(MovieRequest request) { ... }

   @Auditable(action = AuditAction.UPDATE, entityType = "Movie")
   public MovieResponse updateMovie(Long id, MovieRequest request) { ... }

   @Auditable(action = AuditAction.DELETE, entityType = "Movie")
   public void deleteMovie(Long id) { ... }
   ```
   Same pattern for ShowtimeServiceImpl.

4. **booking-service integration:**
   ```java
   @Auditable(action = AuditAction.CREATE, entityType = "Booking")
   public BookingResponse createBooking(BookingRequest request) { ... }

   @Auditable(action = AuditAction.UPDATE, entityType = "Booking")
   public void cancelBooking(Long id) { ... }
   ```

5. **payment-service integration:**
   ```java
   @Auditable(action = AuditAction.CREATE, entityType = "Payment")
   public PaymentResponse initiatePayment(PaymentRequest request) { ... }
   ```

6. **Add @EntityListeners to key entities** in each service (User, Movie, Showtime, Booking, Payment)

7. **Ensure @JsonIgnore on sensitive fields:**
   - User entity: password, tokens must have @JsonIgnore
   - Payment entity: stripe keys, webhook secrets must have @JsonIgnore

8. **Compile all services:** `mvn clean compile`

9. **Integration test:** Start all services, perform operations, verify audit-events in Kafka (via Kafdrop)

## Todo List
- [ ] Verify/add Kafka producer config per service
- [ ] Add @Auditable to auth-service methods
- [ ] Add @Auditable to movie-service methods
- [ ] Add @Auditable to booking-service methods
- [ ] Add @Auditable to payment-service methods
- [ ] Add @EntityListeners to User entity
- [ ] Add @EntityListeners to Movie, Showtime entities
- [ ] Add @EntityListeners to Booking entity
- [ ] Add @EntityListeners to Payment entity
- [ ] Verify @JsonIgnore on sensitive entity fields
- [ ] Compile all modules
- [ ] Integration smoke test via Kafdrop

## Success Criteria
- All 4 business services compile with @Auditable annotations
- Audit events appear in Kafka `audit-events` topic after business operations
- No business logic regressions — existing tests still pass
- Sensitive fields (password, tokens) excluded from audit event JSON

## Risk Assessment
- **Medium:** AOP proxy issues — @Auditable on controller vs service layer matters (must be on Spring-managed bean, external call)
- **Low:** Kafka producer already configured in most services
- **Low:** @JsonIgnore may already exist on sensitive fields — verify before adding duplicates

## Security Considerations
- Password fields MUST have @JsonIgnore to prevent audit trail storing plaintext/hashed passwords
- Token fields MUST be excluded from entity serialization
- User IP captured from HttpServletRequest in AuditAspect (not from entity)

## Next Steps
- Phase 6 sets up infrastructure (Docker, gateway, config)
