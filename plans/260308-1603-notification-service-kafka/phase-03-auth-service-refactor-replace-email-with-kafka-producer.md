# Phase 3: Auth-Service Refactor — Replace Direct Email with Kafka Producer

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1](./phase-01-kafka-events-module-add-notification-events.md)
- [Phase 2](./phase-02-notification-service-setup-consumer-and-email-sender.md)
- [Auth Email Flow Research](./research/researcher-02-auth-email-flow.md)

## Overview
- **Priority:** High
- **Status:** Pending
- **Description:** Replace EmailServiceImpl (direct SMTP) with a Kafka event publisher. Auth-service publishes NotificationRequestedEvent instead of sending emails.

## Key Insights
- EmailService interface has 2 methods: sendActivationEmail, sendPasswordResetEmail
- Called from: ActivationServiceImpl, PasswordResetServiceImpl
- Keep EmailService interface — swap impl from SMTP to Kafka publisher
- Auth-service currently has NO Kafka dependency — needs spring-kafka + kafka-events

## Requirements
- Auth-service publishes to `notification-events` topic instead of sending emails
- Same email content (subject, body) as current implementation
- Remove spring-boot-starter-mail dependency from auth-service
- Remove SMTP config from auth-service application.yml

## Architecture
```
ActivationServiceImpl ──▶ EmailService.sendActivationEmail()
                              │
                              ▼
                    EmailServiceImpl (OLD: JavaMailSender)
                              ↓ replace with ↓
                    NotificationEventPublisher (NEW: KafkaTemplate)
                              │
                              ▼
                    Kafka (notification-events topic)
```

## Related Code Files

**Modify:**
- `auth-service/pom.xml` — add spring-kafka + kafka-events deps, remove spring-mail
- `auth-service/src/main/resources/application.yml` — add kafka config, remove mail config
- `auth-service/src/main/java/.../service/impl/EmailServiceImpl.java` — rewrite to publish Kafka events

**No change needed:**
- `EmailService.java` interface — keep as-is
- `ActivationServiceImpl.java` — still calls emailService.sendActivationEmail()
- `PasswordResetServiceImpl.java` — still calls emailService.sendPasswordResetEmail()

## Implementation Steps

1. **Add dependencies to auth-service/pom.xml:**
   ```xml
   <dependency>
       <groupId>org.springframework.kafka</groupId>
       <artifactId>spring-kafka</artifactId>
   </dependency>
   <dependency>
       <groupId>com.namnd</groupId>
       <artifactId>kafka-events</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```
   Remove: `spring-boot-starter-mail`

2. **Add Kafka config to auth-service application.yml:**
   ```yaml
   spring:
     kafka:
       bootstrap-servers: ${KAFKA_HOST:localhost}:9092
       producer:
         key-serializer: org.apache.kafka.common.serialization.StringSerializer
         value-serializer: org.apache.kafka.common.serialization.StringSerializer
   ```
   Remove: entire `spring.mail` section

3. **Rewrite EmailServiceImpl.java** to publish Kafka events:
   ```java
   @Service
   public class EmailServiceImpl implements EmailService {
       @Autowired
       private KafkaTemplate<String, String> kafkaTemplate;
       @Autowired
       private ObjectMapper objectMapper;

       @Value("${namnd.app.activationBaseUrl}")
       private String activationBaseUrl;
       @Value("${namnd.app.passwordResetBaseUrl}")
       private String passwordResetBaseUrl;

       @Override
       public void sendActivationEmail(String to, String token) {
           String activationLink = activationBaseUrl + "?token=" + token;
           var event = new NotificationRequestedEvent(
               "EMAIL", "ACTIVATION", to,
               "Activate Your Account",
               "Welcome! Click to activate:\n" + activationLink + "\nExpires in 24 hours."
           );
           publishNotification(event);
       }

       @Override
       public void sendPasswordResetEmail(String to, String token) {
           String resetLink = passwordResetBaseUrl + "?token=" + token;
           var event = new NotificationRequestedEvent(
               "EMAIL", "PASSWORD_RESET", to,
               "Password Reset Request",
               "Click to reset password:\n" + resetLink + "\nExpires in 30 minutes."
           );
           publishNotification(event);
       }

       private void publishNotification(NotificationRequestedEvent event) {
           EventEnvelope<NotificationRequestedEvent> envelope =
               EventEnvelope.of("auth-service", "notification.requested",
                   UUID.randomUUID().toString(), event);
           String json = objectMapper.writeValueAsString(envelope);
           kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS,
               event.recipientEmail(), json);
       }
   }
   ```

4. **Remove SMTP config from config-server** auth-service.yml (mail section no longer needed)

5. **Build:** `mvn clean compile -pl auth-service`

## Todo List
- [ ] Add spring-kafka + kafka-events deps to auth-service pom.xml
- [ ] Remove spring-boot-starter-mail from auth-service pom.xml
- [ ] Add Kafka producer config to application.yml
- [ ] Remove mail config from application.yml
- [ ] Rewrite EmailServiceImpl to publish Kafka events
- [ ] Remove mail config from config-server/config-repo/auth-service.yml
- [ ] Verify auth-service compiles

## Success Criteria
- Auth-service compiles without spring-mail dependency
- Registration triggers Kafka event (not direct email)
- Password reset triggers Kafka event (not direct email)
- EmailService interface unchanged — callers unaffected

## Risk Assessment
- **Medium:** If Kafka is down, email events are lost (no outbox pattern). Acceptable for current scale.
- **Low:** Interface unchanged so ActivationServiceImpl/PasswordResetServiceImpl need zero changes

## Security Considerations
- Reset/activation tokens are embedded in URLs in the body — same as current behavior
- Raw tokens NOT stored in Kafka events (only rendered links)
- Kafka topic internal only, not exposed externally

## Next Steps
- Phase 4: wire notification-service into docker-compose
- Phase 5: end-to-end testing
