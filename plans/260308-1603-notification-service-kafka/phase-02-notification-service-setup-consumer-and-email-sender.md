# Phase 2: Notification Service — Setup Consumer & Email Sender

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1](./phase-01-kafka-events-module-add-notification-events.md)
- [Auth Email Flow Research](./research/researcher-02-auth-email-flow.md)
- Booking-service consumer pattern: `booking-service/.../PaymentEventListener.java`

## Overview
- **Priority:** High (core deliverable)
- **Status:** Pending
- **Description:** Create new notification-service Spring Boot module that consumes `notification-events` Kafka topic and sends emails via SMTP

## Key Insights
- Follow booking-service consumer pattern: @KafkaListener + eventType routing via ObjectMapper
- Reuse KafkaConsumerConfig error handling (DLT + exponential backoff)
- SMTP config moves from auth-service to notification-service
- Plain text emails (matching current auth-service behavior)

## Requirements
**Functional:**
- Consume `notification-events` topic
- Deserialize EventEnvelope<NotificationRequestedEvent>
- Send email via JavaMailSender based on event payload
- Dead-letter failed messages after 3 retries

**Non-functional:**
- Stateless (no database needed)
- Registers with Eureka
- Fetches config from Config Server
- Exposes /actuator/prometheus for monitoring

## Architecture
```
Kafka (notification-events)
    │
    ▼
notification-service (:8085)
├── listener/NotificationEventListener.java   ← @KafkaListener
├── service/EmailSenderService.java           ← JavaMailSender
├── config/KafkaConsumerConfig.java           ← DLT + retry
└── NotificationServiceApplication.java
```

## Related Code Files

**Create (new module `notification-service/`):**
- `pom.xml` — Spring Boot, kafka-events dep, spring-mail, spring-kafka, eureka-client, actuator
- `src/main/java/com/namnd/notification/NotificationServiceApplication.java`
- `src/main/java/com/namnd/notification/listener/NotificationEventListener.java`
- `src/main/java/com/namnd/notification/service/EmailSenderService.java`
- `src/main/java/com/namnd/notification/config/KafkaConsumerConfig.java`
- `src/main/resources/application.yml`
- `Dockerfile`

**Modify:**
- `pom.xml` (root) — add `<module>notification-service</module>`

## Implementation Steps

1. **Create module directory**: `notification-service/`

2. **pom.xml** — dependencies:
   - spring-boot-starter
   - spring-kafka
   - spring-boot-starter-mail
   - spring-cloud-starter-netflix-eureka-client
   - spring-boot-starter-actuator
   - micrometer-registry-prometheus
   - kafka-events (project dependency)
   - spring-cloud-starter-config
   - lombok

3. **NotificationServiceApplication.java**:
   ```java
   @SpringBootApplication
   public class NotificationServiceApplication {
       public static void main(String[] args) {
           SpringApplication.run(NotificationServiceApplication.class, args);
       }
   }
   ```

4. **KafkaConsumerConfig.java** — copy pattern from payment-service:
   - DefaultErrorHandler with DeadLetterPublishingRecoverer
   - Exponential backoff: 3 retries (1s → 2s → 4s)
   - Non-retryable: SerializationException, MessageConversionException

5. **NotificationEventListener.java**:
   ```java
   @KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS, groupId = "notification-service")
   public void handleNotificationEvent(String message) {
       EventEnvelope<?> envelope = objectMapper.readValue(message, EventEnvelope.class);
       if ("notification.requested".equals(envelope.eventType())) {
           NotificationRequestedEvent event = objectMapper.convertValue(
               envelope.payload(), NotificationRequestedEvent.class);
           emailSenderService.sendEmail(event);
       }
   }
   ```

6. **EmailSenderService.java**:
   - Inject JavaMailSender
   - `sendEmail(NotificationRequestedEvent event)` method
   - SimpleMailMessage with event.recipientEmail(), event.subject(), event.body()
   - Log success/failure with masked email

7. **application.yml**:
   ```yaml
   server:
     port: ${SERVER_PORT:8085}
   spring:
     application:
       name: notification-service
     config:
       import: "optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888"
     kafka:
       bootstrap-servers: ${KAFKA_HOST:localhost}:9092
       consumer:
         group-id: notification-service
         auto-offset-reset: earliest
         key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
         value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
     mail:
       host: smtp.gmail.com
       port: 587
       username: ${MAIL_USERNAME:}
       password: ${MAIL_PASSWORD:}
       properties:
         mail.smtp.auth: true
         mail.smtp.starttls.enable: true
   eureka:
     client:
       service-url:
         defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
   ```

8. **Dockerfile** (same pattern as other services):
   ```dockerfile
   FROM eclipse-temurin:21-jre-alpine
   WORKDIR /opt/app
   COPY notification-service/target/notification-service.jar notification-service.jar
   ENTRYPOINT ["java", "-jar", "notification-service.jar"]
   ```

## Todo List
- [ ] Create notification-service module directory
- [ ] Write pom.xml with dependencies
- [ ] Add module to root pom.xml
- [ ] Create NotificationServiceApplication
- [ ] Create KafkaConsumerConfig with DLT
- [ ] Create NotificationEventListener
- [ ] Create EmailSenderService
- [ ] Write application.yml
- [ ] Create Dockerfile
- [ ] Build and verify compiles

## Success Criteria
- Module compiles as part of root build
- KafkaListener connects to notification-events topic
- Email sent on receiving valid event
- Failed messages go to DLT after 3 retries

## Risk Assessment
- **SMTP credentials**: Must be configured via env vars, not hardcoded
- **Kafka connection**: Service must handle broker unavailability gracefully (Spring Kafka auto-retry)

## Security Considerations
- SMTP credentials via environment variables only
- No sensitive data logged (use email masking)
- Internal service only — not exposed via API Gateway

## Next Steps
- Phase 3: auth-service publishes events to this topic
- Phase 4: wire into docker-compose
