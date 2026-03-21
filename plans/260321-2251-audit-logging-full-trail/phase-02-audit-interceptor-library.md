# Phase 2: Audit Interceptor Library

## Context Links
- [Plan overview](plan.md)
- [Phase 1 — event model](phase-01-shared-audit-event-model.md)
- [Research: AOP](research/researcher-01-aop-envers-schema.md)
- Existing pattern: `payment-service/.../event/PaymentAfterCommitListener.java`

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 3h
- Create reusable audit interceptors in kafka-events: @Auditable annotation, AuditAspect (AOP), AuditEntityListener (JPA), AuditEventPublisher helper

## Key Insights
- kafka-events is a plain JAR lib — AOP/JPA interceptors need Spring context, so create as autoconfigurable components
- Use `@ConditionalOnBean(KafkaTemplate.class)` so interceptors only activate in services with Kafka
- Extract user context from SecurityContextHolder + MDC (traceId)
- EntityListener can't do constructor injection directly — use `SpringBeanUtil` helper or `@Configurable`
- Use `ApplicationEventPublisher` + `@TransactionalEventListener` pattern (same as payment-service) for after-commit Kafka publish

## Requirements

### Functional
- `@Auditable(action = "CREATE")` annotation on service methods triggers audit event
- AuditAspect captures method args, return value, user context, publishes AuditEvent to Kafka
- AuditEntityListener captures @PrePersist/@PreUpdate/@PreRemove with before/after JSON state
- AuditEventPublisher wraps AuditEvent in EventEnvelope and sends to `audit-events` topic

### Non-Functional
- Must not fail business transaction if audit publish fails (fire-and-forget after commit)
- Performance: aspect overhead < 5ms
- Opt-in only — services must explicitly annotate methods/entities

## Architecture

```
kafka-events/
  src/main/java/com/namnd/kafka/events/
    audit/
      Auditable.java                    (annotation)
      AuditAspect.java                  (AOP around advice)
      AuditEntityListener.java          (JPA lifecycle callbacks)
      AuditEventPublisher.java          (Kafka publish helper)
      AuditSpringEvent.java             (Spring ApplicationEvent for after-commit)
      AuditAfterCommitListener.java     (TransactionalEventListener)
      AuditAutoConfiguration.java       (Spring Boot auto-config)
      AuditBeanProvider.java            (static bean accessor for EntityListener)
```

**Note:** kafka-events pom.xml needs new dependencies: spring-boot-starter-aop, spring-kafka, spring-data-jpa (all `<optional>true</optional>` so consuming services choose what they need).

## Related Code Files

### Create (all in `kafka-events/src/main/java/com/namnd/kafka/events/audit/`)
- `Auditable.java`
- `AuditAspect.java`
- `AuditEntityListener.java`
- `AuditEventPublisher.java`
- `AuditSpringEvent.java`
- `AuditAfterCommitListener.java`
- `AuditAutoConfiguration.java`
- `AuditBeanProvider.java`

### Modify
- `kafka-events/pom.xml` — add optional dependencies
- `kafka-events/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — register auto-config

## Implementation Steps

1. **Add optional dependencies to kafka-events/pom.xml:**
   - `spring-boot-starter-aop` (optional)
   - `spring-kafka` (optional)
   - `spring-boot-starter-data-jpa` (optional)
   - `spring-boot-starter-security` (optional — for SecurityContextHolder)

2. **Create `@Auditable` annotation:**
   ```java
   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface Auditable {
       AuditAction action();
       String entityType() default "";
   }
   ```

3. **Create `AuditSpringEvent`** — simple record holding AuditEvent, published via ApplicationEventPublisher

4. **Create `AuditEventPublisher`:**
   - Inject `KafkaTemplate<String, Object>`
   - Method: `publish(AuditEvent event)` wraps in EventEnvelope, sends to `KafkaTopics.AUDIT_EVENTS`
   - Key: `event.entityType() + ":" + event.entityId()` for partition ordering

5. **Create `AuditAfterCommitListener`:**
   - `@TransactionalEventListener(phase = AFTER_COMMIT)`
   - Delegates to AuditEventPublisher
   - Follow same pattern as `PaymentAfterCommitListener`

6. **Create `AuditAspect`:**
   - `@Aspect @Component @ConditionalOnBean(KafkaTemplate.class)`
   - `@Around("@annotation(auditable)")` advice
   - Extract userId from `SecurityContextHolder`, traceId from MDC
   - Extract entityId from method return value or first arg (convention-based)
   - Publish AuditSpringEvent via ApplicationEventPublisher (triggers after-commit listener)

7. **Create `AuditBeanProvider`:**
   - Static holder for ApplicationContext (set via `@PostConstruct` or `ApplicationContextAware`)
   - Allows EntityListener to access Spring beans

8. **Create `AuditEntityListener`:**
   - `@PrePersist` → action=CREATE, afterState=serialize entity
   - `@PreUpdate` → action=UPDATE, beforeState from managed entity snapshot, afterState=current
   - `@PreRemove` → action=DELETE, beforeState=serialize entity
   - Uses `AuditBeanProvider` to get `ApplicationEventPublisher`
   - Publishes `AuditSpringEvent` (after-commit pattern)
   - **Note:** beforeState on update requires detaching and re-reading from DB or using Hibernate `@DynamicUpdate` — simplify: only capture afterState for v1, add before-state in v2 if needed

9. **Create `AuditAutoConfiguration`:**
   - `@AutoConfiguration`
   - `@ConditionalOnClass(KafkaTemplate.class)`
   - `@Import({AuditAspect.class, AuditEventPublisher.class, AuditAfterCommitListener.class, AuditBeanProvider.class})`

10. **Register auto-config:**
    - Create `kafka-events/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    - Add: `com.namnd.kafka.events.audit.AuditAutoConfiguration`

11. **Compile:** `mvn -pl kafka-events clean compile`

## Todo List
- [ ] Add optional deps to kafka-events pom.xml
- [ ] Create @Auditable annotation
- [ ] Create AuditSpringEvent record
- [ ] Create AuditEventPublisher
- [ ] Create AuditAfterCommitListener
- [ ] Create AuditAspect
- [ ] Create AuditBeanProvider
- [ ] Create AuditEntityListener
- [ ] Create AuditAutoConfiguration
- [ ] Register auto-config in META-INF
- [ ] Compile and verify

## Success Criteria
- kafka-events compiles with optional deps
- @Auditable annotation available for service methods
- AuditEntityListener can be attached to JPA entities
- After-commit pattern prevents audit failures from breaking business transactions

## Risk Assessment
- **Medium:** EntityListener before-state capture is complex — mitigated by deferring to v2
- **Medium:** Optional dependencies may cause classpath issues — mitigated by `@ConditionalOnClass`
- **Low:** AOP proxy issues with internal method calls — document that @Auditable only works on public methods called externally

## Security Considerations
- SecurityContextHolder access in AOP aspect — ensures userId is authenticated
- Entity serialization must exclude sensitive fields (passwords, tokens) — use `@JsonIgnore` on entity fields
- traceId from MDC provides request correlation

## Next Steps
- Phase 3 creates audit-service that consumes the events published here
- Phase 5 integrates @Auditable and EntityListener into business services
