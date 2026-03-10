# Spring Kafka + KRaft Integration Research
**Report Date:** 2026-03-07 | **Status:** Complete

---

## 1. Spring Kafka Dependencies (Spring Boot 3.4.3)

**Compatibility Matrix:**
- Spring Boot 3.4.3 → spring-kafka 3.3.x (included in boot parent BOM)
- Spring Boot 3.4.4+ → spring-kafka 3.3.4+ (GA recommended)
- Kafka Client: 3.8.1+ (compatible with KRaft)

**Maven Dependency:**
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <!-- Version inherited from Spring Boot 3.4.3 BOM (~3.3.2) -->
    <!-- Explicitly override if needed: -->
    <version>3.3.4</version>
</dependency>
```

**Starter Convenience:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
    <!-- Includes spring-kafka + kafka-clients automatically -->
</dependency>
```

---

## 2. Apache Kafka KRaft Mode (No Zookeeper)

**Key Benefit:** Single unified metadata management, production-ready since Kafka 3.3+.

**Docker Compose (Minimal Single-Broker KRaft):**
```yaml
services:
  kafka:
    image: apache/kafka:latest  # 3.9.0+
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: controller,broker
      KAFKA_KRAFT_MODE: true
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@kafka:9093"
      KAFKA_LISTENERS: "PLAINTEXT://kafka:9092,CONTROLLER://kafka:9093"
      KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://kafka:9092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
    ports:
      - "9092:9092"
    volumes:
      - kafka_data:/tmp/kraft-combined-logs
volumes:
  kafka_data:
```

**Connection String (Spring Boot):**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

---

## 3. Producer/Consumer Configuration (Best Practices)

**application.yml:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    # Producer Config
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all                    # Wait for all replicas
      retries: 3
      properties:
        enable.idempotence: true   # Prevent duplicate sends
        max.in.flight.requests.per.connection: 5
        linger.ms: 10              # Batch messages

    # Consumer Config
    consumer:
      group-id: my-service-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      properties:
        spring.json.trusted.packages: "com.namnd.springjwt.event"
        isolation.level: read_committed  # Only read committed messages
```

**Consumer Service (With Error Handling):**
```java
@Service
public class OrderEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "order-service")
    public void consumeOrderEvent(OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition) {
        try {
            processOrder(event);
        } catch (Exception e) {
            // Automatic retry via error handler
            throw new RuntimeException("Order processing failed", e);
        }
    }
}
```

**Producer Service:**
```java
@Service
public class OrderEventProducer {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(OrderEvent event) {
        kafkaTemplate.send("order-events", event.getOrderId(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Sent: {}", event);
                } else {
                    log.error("Failed to send: {}", event, ex);
                }
            });
    }
}
```

---

## 4. Dead Letter Topic (DLT) Configuration

**Enable DLT with Retry:**
```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler() {
        // Retry 3 times with exponential backoff, then send to DLT
        ExponentialBackOffWithMaxRetries backOff =
            new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMaxInterval(10000);

        DefaultErrorHandler handler = new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate()),
            backOff
        );

        // Skip fatal errors (don't retry)
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            MessageConversionException.class
        );

        return handler;
    }
}
```

**DLT Topic Naming:** `{original-topic}.DLT`
- Example: `order-events` → `order-events.DLT`
- Same partition as original message

**DLT Consumer (Optional):**
```java
@KafkaListener(topics = "order-events.DLT")
public void handleFailedOrderEvent(OrderEvent event) {
    log.error("Dead letter: {}", event);
    // Alert, log to database, or manual review queue
}
```

---

## 5. Idempotent Consumer Patterns

**Producer-side Idempotence (Built-in):**
- `enable.idempotence: true` prevents duplicate sends
- Requires `max.in.flight.requests.per.connection ≤ 5`
- Uses sequence numbers per partition

**Consumer-side Idempotence (Application Logic):**
```java
@Service
public class IdempotentOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Transactional
    public void processOrderIdempotent(OrderEvent event) {
        // Check if already processed via unique event ID
        Optional<ProcessedEvent> existing =
            processedEventRepo.findByEventId(event.getEventId());

        if (existing.isPresent()) {
            log.info("Event already processed: {}", event.getEventId());
            return;  // Idempotent: no side effects
        }

        // Process order
        Order order = createOrder(event);
        orderRepository.save(order);

        // Record processed event
        processedEventRepo.save(new ProcessedEvent(event.getEventId()));
    }
}
```

**Database Approach (Recommended):**
```sql
CREATE TABLE processed_events (
    id BIGINT PRIMARY KEY,
    event_id UUID UNIQUE NOT NULL,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 6. Spring Boot Actuator Health Indicator

**Kafka Health Check (Auto-configured):**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
```

**Custom Kafka Health Indicator (Optional):**
```java
@Component
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private AdminClient kafkaAdminClient;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            DescribeClusterResult result =
                kafkaAdminClient.describeCluster();
            int brokers = result.nodes().get().size();

            builder.up()
                .withDetail("brokers", brokers)
                .withDetail("controller", result.controller().get());
        } catch (Exception e) {
            builder.down().withException(e);
        }
    }
}
```

**Health Endpoint Response:**
```
GET /actuator/health
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "brokers": 1,
        "controller": "1@kafka:9093"
      }
    }
  }
}
```

---

## Key Takeaways

| Topic | Recommendation |
|-------|-----------------|
| **Kafka Version** | 3.9.0+ (KRaft GA) |
| **spring-kafka** | 3.3.4+ for Spring Boot 3.4.3+ |
| **Serialization** | JsonSerializer + `spring.json.trusted.packages` |
| **DLT** | DefaultErrorHandler + DeadLetterPublishingRecoverer |
| **Idempotence** | Producer-side: `enable.idempotence=true` + Consumer-side: event dedup table |
| **Health** | Spring Actuator auto-configures `/actuator/health/kafka` |

---

**Sources:**
- [Spring for Apache Kafka](https://spring.io/projects/spring-kafka/)
- [Spring Kafka 3.3.4+ Release](https://spring.io/blog/2025/03/18/spring-kafka-4-0-0-M1-and-3-3-4-and-3-2-8-available-now/)
- [Kafka KRaft Docker Setup](https://www.instaclustr.com/education/apache-spark/running-apache-kafka-kraft-on-docker-tutorial-and-best-practices/)
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Kafka DLT Handling](https://www.confluent.io/blog/spring-kafka-can-your-kafka-consumers-handle-a-poison-pill/)
