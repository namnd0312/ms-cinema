# Research Report: Spring AOP, Envers, Kafka Schema for Audit Logging

**Date:** 2026-03-21
**Project:** ms-cinema audit logging trail
**Status:** Complete

---

## 1. Spring AOP for Audit Logging

### Implementation Pattern
Use `@Target(ElementType.METHOD)` + `@Retention(RetentionPolicy.RUNTIME)` for custom `@Auditable` annotation.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action() default "";
}

@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) {
        Object[] args = pjp.getArgs();
        MethodSignature sig = (MethodSignature) pjp.getSignature();

        try {
            Object result = pjp.proceed();
            publishAuditEvent(sig.getName(), args, result, auditable.action());
            return result;
        } catch (Throwable e) {
            publishAuditEvent(sig.getName(), args, null, auditable.action() + "_FAILED");
            throw new RuntimeException(e);
        }
    }
}
```

### Key Points
- Spring AOP only intercepts **public methods**
- Requires `spring-boot-starter-aop` dependency
- `ProceedingJoinPoint` captures method args, return value, target object
- Extract user context from `SecurityContextHolder.getContext()` in aspect
- Mark aspect with `@Component` to register as Spring bean
- Test thoroughly; monitor performance in production

---

## 2. JPA EntityListener vs Hibernate Envers

### Comparison

| Aspect | JPA EntityListener | Hibernate Envers |
|--------|-------------------|-----------------|
| **Lifecycle coverage** | Partial (@PreRemove fails for deletes) | Complete (including deletes) |
| **Setup complexity** | Simple; lifecycle callbacks only | More; adds audit tables, REVINFO table |
| **Storage** | Custom audit table | Auto-generated `{Entity}_AUD` tables |
| **Query audit history** | Manual queries required | `AuditReader` API; revision-based |
| **Microservices fit** | Good for lightweight metadata | Better for comprehensive history |

### Recommendation for ms-cinema
**Use Hibernate Envers** because:
- Need full state tracking (before/after) for compliance
- Delete operations must be audited (EntityListener limitation)
- Revision-based queries simplify historical lookups
- Auto-table generation reduces boilerplate
- JPA auditing complementary for `createdAt`, `updatedBy` metadata

### Envers Setup
```yaml
spring:
  jpa:
    hibernate:
      dialect: org.hibernate.dialect.PostgreSQL13Dialect
    properties:
      hibernate.envers.audit_table_suffix: _aud
      hibernate.envers.store_data_at_delete: true
      hibernate.envers.global_with_modified_flag: true
```

Annotate entity:
```java
@Entity
@Audited
public class Movie {
    @Id
    private Long id;
    private String title;
}
// Auto-generates: Movie_aud table with REV, REVTYPE, title columns
```

---

## 3. Kafka Audit Event Schema

### Recommended Fields
```json
{
  "eventId": "uuid",
  "eventType": "ENTITY_CREATED|UPDATED|DELETED",
  "timestamp": "2026-03-21T10:30:00Z",
  "version": "1",
  "sourceService": "ms-cinema",

  "userId": "user-123",
  "userIp": "192.168.1.1",
  "traceId": "trace-abc123",
  "correlationId": "corr-xyz789",

  "entityType": "Movie",
  "entityId": "movie-456",
  "action": "CREATE",

  "beforeState": "{...entity snapshot...}",
  "afterState": "{...entity snapshot...}",
  "changes": ["title", "releaseDate"],

  "status": "SUCCESS|FAILED",
  "errorMessage": null
}
```

### Jackson Serialization
```java
public class AuditEvent {
    public static String serializeEntity(Object entity) {
        try {
            return new ObjectMapper().writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
```

### Schema Evolution
- Use **Confluent Schema Registry** or **JSON Schema** for schema versioning
- Add `version` field; allow nullable new fields for backward compatibility
- Headers (Kafka record headers) carry metadata (userId, traceId) separately from payload

### Topic Design
- Topic: `audit-events` (single topic, partition by `correlationId`)
- Or: Partition by `sourceService` for service-specific subscriptions
- Retention: 90 days (compliance requirement)

---

## Summary
1. **AOP**: Simple method-level auditing; use `@Around` + `ProceedingJoinPoint` for args/return capture
2. **Envers**: Full entity state tracking; auto audit tables; better than EntityListener for microservices
3. **Schema**: Include trace context, before/after state, entity metadata; use Jackson + Schema Registry for evolution

---

## Sources
- [Baeldung: Spring AOP Custom Annotation](https://www.baeldung.com/spring-aop-annotation)
- [Baeldung: Database Auditing with JPA/Hibernate](https://www.baeldung.com/database-auditing-jpa)
- [Hibernte Envers Official Docs](https://docs.jboss.org/envers/docs/)
- [Medium: JPA Auditing + Envers Examples](https://medium.com/@sarveshkhamkar321/audit-trail-in-spring-boot-jpa-auditing-hibernate-envers-with-examples-cb32bcc8fc32)
- [Confluent: Kafka Event Schema Design](https://oneuptime.com/blog/post/2026-01-21-kafka-event-schemas/view)
- [Baeldung: AOP Method Interception](https://www.baeldung.com/spring-aop-get-advised-method-info)
