# Kafka-based Real-Time Notification System Research
## Spring Boot 3.4.x + SSE + Angular EventSource

### 1. Kafka Consumer → SSE Push Pattern

**Architecture:** Spring WebFlux Reactive Kafka consumer reads from Kafka topic & pushes via SSE.

```java
@RestController
@RequestMapping("/api/notifications")
public class NotificationSSEController {
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @GetMapping("/stream")
    public SseEmitter subscribe(@RequestParam String userId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5min timeout
        kafkaConsumerService.addEmitter(userId, emitter);
        return emitter;
    }
}

@Service
public class NotificationKafkaConsumer {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @KafkaListener(topics = "notifications", groupId = "notification-sse")
    public void consume(NotificationEvent event) {
        List<SseEmitter> userEmitters = emitters.getOrDefault(event.getUserId(), List.of());
        userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .id(event.getId())
                    .name("notification")
                    .data(event)
                    .reconnectTime(5000)
                    .build());
            } catch (IOException e) {
                userEmitters.remove(emitter);
            }
        });
    }
}
```

**Key Insight:** Use `SseEmitter` (servlet) for traditional stack or WebFlux `Flux<ServerSentEvent>` for reactive. Maintain user→emitter mapping for targeted delivery.

---

### 2. Broadcasting to Multiple Users (Admin Pattern)

**Pattern:** Single Kafka topic with message routing via headers/filters or multi-group consumers.

```java
@Service
public class BroadcastNotificationService {
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void broadcastToAdmins(AdminNotification notification) {
        NotificationEvent event = NotificationEvent.builder()
            .id(UUID.randomUUID().toString())
            .type("ADMIN_BROADCAST")
            .recipientRoles(List.of("ADMIN"))
            .payload(notification.getMessage())
            .createdAt(Instant.now())
            .build();

        kafkaTemplate.send("notifications", event.getId(), event);
    }
}

@Service
public class RoleBasedNotificationConsumer {
    @KafkaListener(topics = "notifications", groupId = "admin-notification-group")
    public void consumeAdminNotifications(NotificationEvent event) {
        if (event.getRecipientRoles().contains("ADMIN")) {
            // Deliver to admin SSE emitters only
            deliverToAdmins(event);
        }
    }
}
```

**Design Choice:** Use separate consumer groups per role/broadcast type. Alternative: single topic with client-side filtering based on subscription preferences.

---

### 3. Notification Persistence Pattern

**Recommended: Outbox Pattern** - Save to DB + Kafka in single transaction.

```java
@Service
@Transactional
public class NotificationPersistenceService {
    private final NotificationRepository repository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public void createAndPublish(CreateNotificationRequest req) {
        // 1. Persist to DB
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .userId(req.getUserId())
            .message(req.getMessage())
            .status(NotificationStatus.UNREAD)
            .createdAt(Instant.now())
            .build();
        repository.save(notification);

        // 2. Publish to Kafka (same transaction)
        NotificationEvent event = NotificationEvent.from(notification);
        kafkaTemplate.send("notifications", event.getId(), event);
    }
}

// For retrieval of missed notifications during offline periods
@GetMapping("/history")
public List<NotificationDTO> getNotificationHistory(
    @RequestParam String userId,
    @RequestParam(defaultValue = "20") int limit) {
    return notificationRepository.findLatestByUserId(userId, limit);
}
```

**Benefit:** Guarantees exactly-once delivery + historical retrieval for reconnected clients.

---

### 4. Kafka Topic Design for Notifications

**Recommendation: Single topic with event type discrimination**

```properties
# application.yml
kafka:
  topic: notifications
  partitions: 3
  replication-factor: 2

# Event types routed by message type field
EventType:
  - USER_NOTIFICATION (single user)
  - ADMIN_BROADCAST (filtered by role)
  - COMMENT_MENTION (user mention in comment)
  - VOTE_UPDATE (vote events)
```

**Rationale:**
- **Single Topic Advantages:** Maintains event order per user (using userId as partition key), simpler consumer management, easier global ordering if needed.
- **Topic Strategy:** Partition by `userId` or `recipientId` → ensures all notifications for one user go to same partition → ordered consumption.

```java
kafkaTemplate.send("notifications",
    event.getUserId(), // partition key
    event.getId(),     // message key
    event);            // value
```

**Alternative:** Multiple topics (user-notification, admin-broadcast, mention-events) if strict schema separation required.

---

### 5. Angular EventSource Client with Reconnect

```typescript
// notification.service.ts
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private eventSource: EventSource | null = null;
  public notifications$ = new Subject<Notification>();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;

  connect(userId: string): void {
    this.eventSource = new EventSource(
      `/api/notifications/stream?userId=${userId}`,
      { withCredentials: true }
    );

    this.eventSource.addEventListener('notification', (event: any) => {
      const notification = JSON.parse(event.data);
      this.notifications$.next(notification);
      this.reconnectAttempts = 0; // reset on success
    });

    this.eventSource.addEventListener('open', () => {
      console.log('SSE connected');
    });

    this.eventSource.addEventListener('error', (error: any) => {
      console.error('SSE error:', error);
      if (this.eventSource?.readyState === EventSource.CLOSED) {
        this.handleReconnect(userId);
      }
    });
  }

  private handleReconnect(userId: string): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
      setTimeout(() => this.connect(userId), delay);
    }
  }

  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }
}

// Usage in component
@Component({...})
export class NotificationListComponent implements OnInit, OnDestroy {
  notifications$ = this.notificationService.notifications$;

  constructor(
    private notificationService: NotificationService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.authService.getCurrentUserId().subscribe(userId => {
      this.notificationService.connect(userId);
    });
  }

  ngOnDestroy(): void {
    this.notificationService.disconnect();
  }
}
```

**Client-side Features:**
- Exponential backoff reconnection (1s → 2s → 4s... → 30s max)
- Event ID tracking via browser cache for Last-Event-ID header
- `withCredentials: true` for authenticated sessions

---

## Summary

| Component | Pattern | Key Decision |
|-----------|---------|--------------|
| **Consumer** | Spring Kafka + SseEmitter | Maintain user→emitter registry |
| **Broadcast** | Multi-consumer group or topic header filtering | Separate groups per role |
| **Persistence** | Outbox pattern (DB + Kafka transaction) | Exactly-once + history |
| **Topic** | Single topic, partitioned by userId | Event order guarantee |
| **Client** | EventSource + exponential backoff | Robust reconnection handling |

---

## Sources
- [Server-Sent Events using Reactive Kafka and Spring WebFlux](https://www.confluent.io/events/kafka-summit-europe-2021/server-sent-events-using-reactive-kafka-and-spring-web-flux/)
- [Scalable Real-Time Notifications Using Kafka and SSE](https://blog.devgenius.io/scalable-real-time-notifications-using-kafka-and-sse-cecc131462c2)
- [Multiple Event Types in the Same Kafka Topic](https://www.confluent.io/blog/put-several-event-types-kafka-topic/)
- [Using Server-Sent Events - MDN Web Docs](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- [ReconnectingEventSource on GitHub](https://github.com/fanout/reconnecting-eventsource)
- [Outbox Pattern with Apache Kafka](https://axual.com/blog/implementing-outbox-pattern-with-apache-kafka-and-spring-modulith)
- [Event Sourcing with Spring Boot and Apache Kafka](https://www.confluent.io/kafka-summit-san-francisco-2019/event-sourcing-with-spring-boot-and-apache-kafka/)
