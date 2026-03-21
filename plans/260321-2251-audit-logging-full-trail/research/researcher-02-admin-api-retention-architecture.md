# Audit Logging: Admin API, Retention, Architecture Research

**Date**: 2026-03-21 | **Token Budget**: 5 tool calls

## 1. Admin API Design for Audit Log Querying

### REST API Pattern
Use **JPA Specifications** with `JpaSpecificationExecutor` for dynamic filtering:
- Filter by: user ID, action type (INSERT/UPDATE/DELETE), entity type, timestamp range
- Pagination: `PageRequest(page, size, Sort.by(...))` returns `Page<AuditLog>` with total count
- Sorting: Support sort by timestamp, user, entity, action

**Implementation**: Create `AuditLogSpecification` class with static methods:
```java
public static Specification<AuditLog> byUserId(UUID userId) {
  return (root, query, cb) -> cb.equal(root.get("userId"), userId);
}
```

### Endpoint Design
```
GET /api/v1/admin/audit-logs?userId={id}&action={action}&entityType={type}&startDate={iso}&endDate={iso}&page=0&size=20&sort=createdAt,desc
Response: {
  "content": [...], "totalElements": 10000, "totalPages": 500,
  "number": 0, "size": 20, "first": true, "last": false
}
```

**Recommendations**:
- Expose UUID/timestamp in query parameters (not numeric IDs)
- Default page size=20, max=100 to prevent abuse
- Use ISO-8601 timestamps for date ranges
- Index on: userId, action, entityType, createdAt for query performance

---

## 2. Retention Policy & Data Volume

### Storage Estimate (1000 events/day)
- Per record: ~500 bytes (id, userId, action, entityType, changes as JSON, timestamp, metadata)
- Daily: 500 KB
- Annual: ~182 MB
- 3-year retention: ~546 MB (manageable)

### PostgreSQL Partitioning Strategy
**Use Range Partitioning by date** (monthly for small-medium projects):

```sql
CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,  -- idempotency key
  user_id UUID NOT NULL,
  action VARCHAR(20) NOT NULL,    -- INSERT, UPDATE, DELETE, READ
  entity_type VARCHAR(100) NOT NULL,
  entity_id UUID NOT NULL,
  changes JSONB,
  created_at TIMESTAMPTZ NOT NULL,
  created_by VARCHAR(255),
  ip_address INET,
  session_id VARCHAR(255)
) PARTITION BY RANGE (DATE_TRUNC('month', created_at));

CREATE TABLE audit_logs_2026_01 PARTITION OF audit_logs
  FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
```

**Indexes**:
```sql
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_event_id ON audit_logs(event_id) UNIQUE;  -- idempotency
```

### Retention & Archival
- **Scheduled Job** (PostgreSQL cron or Spring Scheduler): Archive partitions >12 months to separate archive schema
- **Quick drop**: `DROP TABLE audit_logs_2025_01` takes milliseconds vs. DELETE rows (hours)
- **Tool**: Use `pg_partman` extension for automatic partition creation & retention

---

## 3. Audit Service Architecture

### Recommendation: **Embedded Service (Not Microservice)**
For small-medium project, embed audit logging in each service rather than separate microservice:

**Why**:
- Avoid distributed transaction complexity
- Single database transaction covers business logic + audit
- No network latency for audit writes
- Easier testing & debugging

### Implementation Pattern

**Kafka Consumer (if audit events from other services)**:
```java
@KafkaListener(topics = "audit-events", groupId = "audit-processor")
public void processAuditEvent(AuditEventMessage event) {
  // Idempotency check
  if (auditLogRepository.existsByEventId(event.getEventId())) {
    return;  // Already processed
  }
  auditLogRepository.save(auditEventMapper.toDomain(event));
}
```

**Idempotency Strategy**:
- Use `event_id` as unique constraint (natural deduplication key)
- Kafka retries won't create duplicates in DB
- For HTTP APIs: Check `event_id` before processing

### Database Schema (Shown Above)
- Add `event_id` (UUID) as unique constraint
- Add `session_id` for user session tracking
- JSONB `changes` column for capturing old/new values

### Microservice Topology
If audit must be centralized:
- **Single audit-service** consuming from Kafka topic
- Each service publishes `AuditEventMessage` after business operation succeeds
- Idempotent consumer with deduplication by `event_id`
- Use transactional outbox pattern if critical: business log → outbox table → Kafka

---

## Key Recommendations

1. **Admin API**: Use Specifications for filtering, Page for pagination, expose via REST with proper indexing
2. **Storage**: Monthly partitioning + pg_partman for auto-archival, ~546 MB for 3-year retention (small)
3. **Architecture**: Embed audit in services (not separate microservice) to avoid distributed transaction overhead
4. **Idempotency**: Always use `event_id` UUID as unique key for deduplication
5. **Performance**: Index userId, action, entityType, createdAt; partition by date; max 100 records/page

---

## Unresolved Questions

- What compliance retention requirement? (90 days vs. 3 years changes partition strategy)
- Should archived data remain queryable via API? (Use parquet_fdw if yes)
- Is read audit (SELECT queries) needed or only write audit (INSERT/UPDATE/DELETE)?
