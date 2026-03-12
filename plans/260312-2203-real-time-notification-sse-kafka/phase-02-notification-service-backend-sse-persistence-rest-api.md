# Phase 2: Notification-Service Backend — SSE, Persistence, REST API

## Context Links
- [NotificationEventListener.java](../../notification-service/src/main/java/com/namnd/notification/listener/NotificationEventListener.java)
- [KafkaConsumerConfig.java](../../notification-service/src/main/java/com/namnd/notification/config/KafkaConsumerConfig.java)
- [notification-service application.yml](../../notification-service/src/main/resources/application.yml)
- [notification-service pom.xml](../../notification-service/pom.xml)
- [JwtTokenValidator.java](../../jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java)
- [SSE Research](./research/researcher-sse-spring-boot.md)
- [Kafka Notification Research](./research/researcher-kafka-notification-pattern.md)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h

Major extension of notification-service: add PostgreSQL persistence (Notification entity), SSE infrastructure (SseEmitter registry + heartbeat), new Kafka consumer for in-app events, and REST endpoints for notification history/management.

## Key Insights
- notification-service currently has NO database, NO REST controllers, NO SSE
- Already has spring-boot-starter-web (SseEmitter available), spring-kafka, redis
- Must add spring-data-jpa + postgresql driver dependencies
- JWT auth via query param for SSE (EventSource has no header support)
- Use jwt-auth-spring-boot-starter for token validation (reuse `JwtTokenValidator`)
- Existing `NotificationEventListener` handles email; add separate listener for in-app to avoid coupling
- Use unique consumer group per instance `notification-sse-{instanceId}` so ALL instances receive every message (broadcast pattern). Each instance checks if it holds SSE for that userId before pushing. This enables zero-code-change scaling.
- 5-minute SseEmitter timeout with 30s heartbeat to prevent proxy/gateway closure

## Requirements

### Functional
- Notification JPA entity persisted to `notificationdb` PostgreSQL database
- SSE endpoint: `GET /api/notifications/stream?token=JWT` — streams events to authenticated user
- REST endpoints:
  - `GET /api/notifications?page=0&size=20` — paginated user notifications
  - `PATCH /api/notifications/{id}/read` — mark single notification as read
  - `PATCH /api/notifications/read-all` — mark all user notifications as read
  - `GET /api/notifications/unread-count` — return unread count
- Kafka consumer: listen to `notification-events` with eventType `notification.in_app`, save to DB, push to SSE
- Admin broadcast: `POST /api/notifications/broadcast` (ADMIN role required) — publish InAppNotificationEvent to Kafka
- Heartbeat every 30s to keep SSE connection alive

### Non-functional
- SseEmitter timeout: 5 minutes (300,000ms)
- Thread-safe emitter registry using ConcurrentHashMap + CopyOnWriteArrayList
- Graceful cleanup on disconnect/timeout/error
- Files < 200 LOC each

## Architecture

```
notification-service/src/main/java/com/namnd/notification/
├── config/
│   ├── KafkaConsumerConfig.java          (existing, unchanged)
│   ├── KafkaProducerConfig.java          ← NEW (for admin broadcast)
│   └── SecurityConfig.java               ← NEW (JWT validation for SSE + REST)
├── controller/
│   ├── NotificationSseController.java    ← NEW (SSE stream endpoint)
│   └── NotificationRestController.java   ← NEW (CRUD + broadcast)
├── dto/
│   ├── NotificationResponseDto.java      ← NEW
│   ├── BroadcastRequestDto.java          ← NEW
│   └── UnreadCountResponseDto.java       ← NEW
├── model/
│   └── Notification.java                 ← NEW (JPA entity)
├── repository/
│   └── NotificationRepository.java       ← NEW
├── service/
│   ├── EmailSenderService.java           (existing, unchanged)
│   ├── NotificationDeduplicationService.java (existing, unchanged)
│   ├── SseEmitterRegistryService.java    ← NEW
│   ├── InAppNotificationService.java     ← NEW (save + push)
│   └── impl/
│       └── InAppNotificationServiceImpl.java ← NEW
├── listener/
│   ├── NotificationEventListener.java    (existing, unchanged — email only)
│   └── InAppNotificationEventListener.java ← NEW (in-app SSE)
└── NotificationServiceApplication.java   (existing, unchanged)
```

## Related Code Files

### Files to Create (14 files)
1. `config/KafkaProducerConfig.java` — KafkaTemplate bean for admin broadcast
2. `config/SecurityConfig.java` — JWT token validation, permit SSE/REST endpoints
3. `controller/NotificationSseController.java` — SSE stream endpoint
4. `controller/NotificationRestController.java` — GET/PATCH/POST endpoints
5. `dto/NotificationResponseDto.java` — API response record
6. `dto/BroadcastRequestDto.java` — admin broadcast request body
7. `dto/UnreadCountResponseDto.java` — unread count response
8. `model/Notification.java` — JPA entity
9. `repository/NotificationRepository.java` — Spring Data JPA
10. `service/SseEmitterRegistryService.java` — emitter lifecycle management
11. `service/InAppNotificationService.java` — interface
12. `service/impl/InAppNotificationServiceImpl.java` — save + push logic
13. `listener/InAppNotificationEventListener.java` — Kafka consumer for in-app

### Files to Modify
1. `pom.xml` — add spring-data-jpa, postgresql, jwt-auth-starter dependencies
2. `application.yml` — add datasource, JPA config

### Files Unchanged
- `NotificationEventListener.java` — keep email handling as-is
- `EmailSenderService.java`
- `NotificationDeduplicationService.java`
- `KafkaConsumerConfig.java`

## Implementation Steps

### Step 1: Add Dependencies to pom.xml

Add to `notification-service/pom.xml`:
```xml
<!-- JPA + PostgreSQL for notification persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- JWT auth for SSE + REST endpoints -->
<dependency>
    <groupId>com.namnd</groupId>
    <artifactId>jwt-auth-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Update application.yml

Add datasource and JPA config:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/notificationdb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

### Step 3: Create Notification JPA Entity

```java
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### Step 4: Create NotificationRepository

```java
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}
```

### Step 5: Create DTOs

- `NotificationResponseDto(Long id, String title, String message, String notificationType, boolean isRead, LocalDateTime createdAt)`
- `BroadcastRequestDto(String title, String message)` — validated with @NotBlank
- `UnreadCountResponseDto(long count)`

### Step 6: Create SseEmitterRegistryService

- `ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>` for userId→emitters
- `addEmitter(Long userId, SseEmitter emitter)` — register + set onCompletion/onTimeout/onError callbacks for cleanup
- `removeEmitter(Long userId, SseEmitter emitter)` — remove from list
- `sendToUser(Long userId, Object data)` — send SSE event to specific user's emitters
- `broadcastToAll(Object data)` — iterate all emitters, send to everyone
- `@Scheduled(fixedRate = 30000)` heartbeat — send comment event to all active emitters

### Step 7: Create InAppNotificationService + Impl

Interface methods:
- `NotificationResponseDto saveAndPush(InAppNotificationEvent event)` — persist + SSE push
- `Page<NotificationResponseDto> getUserNotifications(Long userId, Pageable pageable)`
- `void markAsRead(Long notificationId, Long userId)`
- `void markAllAsRead(Long userId)`
- `long getUnreadCount(Long userId)`
- `void broadcast(BroadcastRequestDto request)` — publish to Kafka

### Step 8: Create InAppNotificationEventListener

```java
// Unique group per instance → all instances receive every message (broadcast pattern)
// Enables multi-instance scaling with zero code changes
@KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS,
    groupId = "notification-sse-#{T(java.util.UUID).randomUUID().toString().substring(0,8)}")
public void handleInAppEvent(EventEnvelope envelope) {
    if (!"notification.in_app".equals(envelope.eventType())) return;
    InAppNotificationEvent event = objectMapper.convertValue(
        envelope.payload(), InAppNotificationEvent.class);
    // Save to DB (use dedup to avoid duplicate saves across instances)
    // Push via SSE only if this instance holds emitter for event.userId()
    inAppNotificationService.saveAndPush(event);
}
```

### Step 9: Create SecurityConfig

- Reuse `JwtTokenValidator` from jwt-auth-starter
- For SSE endpoint: extract token from `?token=` query param, validate, set SecurityContext
- For REST endpoints: extract from Authorization header (standard Bearer flow)
- Permit actuator/health endpoints without auth

### Step 10: Create NotificationSseController

```java
@GetMapping("/api/notifications/stream")
public SseEmitter stream(@RequestParam("token") String token) {
    // Validate JWT, extract userId
    // Create SseEmitter(300_000L)
    // Register in SseEmitterRegistryService
    // Send initial connection event
    return emitter;
}
```

### Step 11: Create NotificationRestController

- `GET /api/notifications` — paginated list (userId from SecurityContext)
- `PATCH /api/notifications/{id}/read` — mark as read
- `PATCH /api/notifications/read-all` — mark all as read
- `GET /api/notifications/unread-count` — unread count
- `POST /api/notifications/broadcast` — admin only, publish to Kafka

### Step 12: Create KafkaProducerConfig

Standard KafkaTemplate<String, Object> bean with JSON serializer for admin broadcast publishing.

### Step 13: Compile and verify

```bash
mvn clean compile -pl notification-service
```

## Todo List
- [ ] Add JPA + PostgreSQL + jwt-auth-starter dependencies to pom.xml
- [ ] Update application.yml with datasource + JPA config
- [ ] Create Notification entity
- [ ] Create NotificationRepository
- [ ] Create DTOs (NotificationResponseDto, BroadcastRequestDto, UnreadCountResponseDto)
- [ ] Create SseEmitterRegistryService with heartbeat
- [ ] Create InAppNotificationService interface
- [ ] Create InAppNotificationServiceImpl
- [ ] Create InAppNotificationEventListener (Kafka consumer)
- [ ] Create SecurityConfig (JWT validation)
- [ ] Create NotificationSseController (SSE endpoint)
- [ ] Create NotificationRestController (CRUD + broadcast)
- [ ] Create KafkaProducerConfig
- [ ] Verify compilation

## Success Criteria
- Notification entity auto-creates table in notificationdb
- SSE endpoint returns SseEmitter with 5-min timeout
- Heartbeat fires every 30s
- Kafka consumer saves InAppNotificationEvent to DB and pushes via SSE
- REST endpoints return paginated notifications, update read status, return unread count
- Admin broadcast publishes to Kafka and reaches all connected clients

## Risk Assessment
- **SseEmitter memory leak**: Mitigated by onCompletion/onTimeout/onError cleanup callbacks
- **Thread safety**: ConcurrentHashMap + CopyOnWriteArrayList handles concurrent access
- **JWT in query param**: Token visible in server logs; mitigate by not logging query params
- **Database not created**: Must create `notificationdb` database before startup (Phase 6)

## Security Considerations
- JWT validated on SSE connect — reject invalid/expired tokens immediately
- REST endpoints require valid JWT in Authorization header
- Admin broadcast requires ROLE_ADMIN
- Mark-as-read verifies userId ownership
- Rate limit consideration: SSE reconnect could flood; 5s retry interval set on client

## Next Steps
- Phase 3: booking-service publishes InAppNotificationEvent
- Phase 4: API Gateway routes to notification-service
