# Spring Boot 3.4.x SSE Implementation Research

## 1. SseEmitter Timeout & Connection Management

**Best Practice Pattern:**
```java
SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
emitter.onCompletion(() -> { /* cleanup */ });
emitter.onTimeout(() -> { /* handle timeout */ });
emitter.onError(throwable -> { /* handle error */ });
return emitter;
```

- Default timeout: 30s (configurable)
- Use `0L` for infinite timeout but handle heartbeat
- Implement callbacks for resource cleanup
- Send heartbeat every 15-30s to prevent proxy closure
- Handle `AsyncRequestTimeoutException` explicitly

## 2. JWT Authentication with SSE

**Key Finding:** JWT for SSE differs from standard HTTP. Options:

1. **Query Parameter Approach:**
   - Pass token: `GET /sse/subscribe?token=JWT_TOKEN`
   - Decode token in controller before returning emitter
   - Stateless, but cache tokens carefully

2. **Spring Security Reuse:**
   - SSE inherits HttpServletRequest authentication
   - Configure Spring Security with JWT filter
   - Principal automatically available in SSE endpoint
   - Same CORS/CSRF policies apply

**Note:** WebSocket security patterns (CSRF token in CONNECT message) don't apply to SSE HTTP streaming.

## 3. Spring Cloud Gateway SSE Routing

**Configuration:**
```yaml
routes:
  - id: sse
    uri: http://backend:8080
    predicates:
      - Path=/sse/**
    filters:
      - DedupeResponseHeader=Access-Control-Allow-Origin
      - SetResponseHeader=Access-Control-Allow-Credentials, true
```

**Critical Issues:**
- MVC version leaks connections (use Reactive version instead)
- Response flushing delays until 2nd message arrives
- Partial stream blocking reported
- Recommend Reactive/Project Reactor for streaming

## 4. Concurrent Emitters - Thread Safety

**Recommended Pattern:**
```java
private CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

public void addEmitter(SseEmitter emitter) {
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
}

public void broadcast(String msg) {
    emitters.forEach(e -> {
        try {
            e.send(SseEmitter.event().data(msg));
        } catch (IOException ex) {
            emitters.remove(e);
        }
    });
}
```

**Advanced:** Streamline library uses `ConcurrentHashMap` + `ReentrantLock` + `CopyOnWriteArraySet` for complex scenarios. Virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) viable for high concurrency.

## 5. Reconnection & Event IDs

**Implementation:**
```java
@GetMapping("/subscribe")
public SseEmitter subscribe(@RequestHeader(value = "Last-Event-ID", required = false) String lastId) {
    SseEmitter emitter = new SseEmitter(300_000L);

    // Send missed events first
    if (lastId != null) {
        eventHistory.stream()
            .filter(e -> Long.parseLong(e.id) > Long.parseLong(lastId))
            .forEach(e -> {
                try {
                    emitter.send(SseEmitter.event()
                        .id(e.id)
                        .name(e.name)
                        .data(e.data)
                        .retry(3000));
                } catch (IOException ex) { }
            });
    }
    emitters.add(emitter);
    return emitter;
}
```

**Key Points:**
- Client auto-reconnects; send `Last-Event-ID` header
- Server maintains event history (in-memory or DB)
- Assign unique IDs (`id` field); filter on reconnect
- Set `retry` field (milliseconds) for reconnection interval
- Track event sequence for recovery

## Architecture Notes

- **Spring Boot 3.x** uses Jakarta EE (not javax)
- Prefer **Reactive Stack** (WebFlux) over MVC for SSE at scale
- **Kafka integration**: Use `@KafkaListener` to consume, broadcast via `SseEmitter`
- **Stateless**: Store emitters in-memory (singleton) or distributed cache for multi-instance

## Unresolved Questions

1. How to persist event history across service restarts?
2. Should event IDs be UUIDs or sequential for distributed systems?
3. What's optimal emitter cleanup strategy under high churn?

---

**Sources:**
- [Baeldung: Server-Sent Events in Spring](https://www.baeldung.com/spring-server-sent-events)
- [Spring Cloud Gateway SSE Issues](https://github.com/spring-cloud/spring-cloud-gateway/issues/1550)
- [Streamline Library (Advanced Registry)](https://github.com/kusoroadeolu/streamline-spring-boot-starter)
- [JWT WebSocket/Streaming Auth](https://medium.com/@poojithairosha/spring-boot-3-authenticate-websocket-connections-with-jwt-tokens-2b4ff60532b6)
- [InfoQ: Reactive Notifications with SSE & Redis](https://www.infoq.com/articles/reactive-notification-system-server-sent-events/)
