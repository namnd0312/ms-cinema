# Phase 4: Admin API

## Context Links
- [Plan overview](plan.md)
- [Phase 3 — audit-service](phase-03-create-audit-service.md)
- [Research: admin API design](research/researcher-02-admin-api-retention-architecture.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- REST API for admins to search/filter audit logs with JPA Specifications, pagination, sorting

## Key Insights
- JPA Specifications allow composable dynamic filtering without query builder boilerplate
- Reuse Spring Data Page/Pageable for standard pagination response
- Max page size capped at 100 to prevent abuse
- Index on userId, action, entityType, createdAt for query performance

## Requirements

### Functional
- `GET /api/v1/admin/audit-logs` — search with filters: userId, action, entityType, entityId, startDate, endDate
- Pagination: page, size (default 20, max 100), sort (default createdAt desc)
- Response: Spring Page wrapper with content, totalElements, totalPages, etc.
- `GET /api/v1/admin/audit-logs/{id}` — single audit log detail

### Non-Functional
- Admin-only (ADMIN role required)
- Response time < 500ms for filtered queries with proper indexing

## Architecture

```
audit-service/src/main/java/com/namnd/auditservice/
  controller/
    AdminAuditLogController.java
  dto/
    AuditLogSearchRequest.java     (query params DTO)
    AuditLogResponse.java          (response DTO)
  specification/
    AuditLogSpecification.java     (JPA Specification builder)
  mapper/
    AuditLogMapper.java            (MODIFY — add toResponse mapping)
```

## Related Code Files

### Create
- `audit-service/src/main/java/com/namnd/auditservice/controller/AdminAuditLogController.java`
- `audit-service/src/main/java/com/namnd/auditservice/dto/AuditLogSearchRequest.java`
- `audit-service/src/main/java/com/namnd/auditservice/dto/AuditLogResponse.java`
- `audit-service/src/main/java/com/namnd/auditservice/specification/AuditLogSpecification.java`

### Modify
- `audit-service/src/main/java/com/namnd/auditservice/mapper/AuditLogMapper.java` — add toResponse()
- `audit-service/src/main/java/com/namnd/auditservice/config/SecurityConfig.java` — secure admin endpoints

## Implementation Steps

1. **Create `AuditLogSearchRequest`:**
   ```java
   public record AuditLogSearchRequest(
       String userId,
       AuditAction action,
       String entityType,
       String entityId,
       LocalDateTime startDate,
       LocalDateTime endDate
   ) {}
   ```

2. **Create `AuditLogResponse`:**
   ```java
   public record AuditLogResponse(
       Long id,
       String eventId,
       String userId,
       String userIp,
       String action,
       String entityType,
       String entityId,
       Object beforeState,    // parsed JSON
       Object afterState,     // parsed JSON
       String sourceService,
       String traceId,
       LocalDateTime createdAt
   ) {}
   ```

3. **Create `AuditLogSpecification`:**
   - Static methods returning `Specification<AuditLog>`:
     - `byUserId(String userId)` — equal
     - `byAction(AuditAction action)` — equal
     - `byEntityType(String entityType)` — equal
     - `byEntityId(String entityId)` — equal
     - `byDateRange(LocalDateTime start, LocalDateTime end)` — between
   - Combiner: `buildSpecification(AuditLogSearchRequest req)` — chains non-null specs with AND

4. **Create `AdminAuditLogController`:**
   ```java
   @RestController
   @RequestMapping("/api/v1/admin/audit-logs")
   @PreAuthorize("hasRole('ADMIN')")
   public class AdminAuditLogController {

       @GetMapping
       public Page<AuditLogResponse> search(
           @ModelAttribute AuditLogSearchRequest request,
           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
       ) {
           Pageable capped = PageRequest.of(
               pageable.getPageNumber(),
               Math.min(pageable.getPageSize(), 100),
               pageable.getSort()
           );
           Specification<AuditLog> spec = AuditLogSpecification.buildSpecification(request);
           return repository.findAll(spec, capped).map(AuditLogMapper::toResponse);
       }

       @GetMapping("/{id}")
       public AuditLogResponse getById(@PathVariable Long id) {
           return AuditLogMapper.toResponse(
               repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Audit log not found"))
           );
       }
   }
   ```

5. **Add toResponse() to AuditLogMapper:**
   - Parse beforeState/afterState JSON strings to Object (Map) for cleaner API response
   - Use ObjectMapper.readValue with fallback to raw string

6. **Update SecurityConfig:**
   - `/api/v1/admin/**` requires ADMIN role
   - Health/actuator endpoints remain public

7. **Add database indexes** (in application.yml or entity annotations):
   ```java
   @Table(name = "audit_logs", indexes = {
       @Index(name = "idx_audit_user_id", columnList = "userId"),
       @Index(name = "idx_audit_action", columnList = "action"),
       @Index(name = "idx_audit_entity_type", columnList = "entityType"),
       @Index(name = "idx_audit_created_at", columnList = "createdAt")
   })
   ```

8. **Compile and test:** `mvn -pl audit-service clean compile`

## Todo List
- [ ] Create AuditLogSearchRequest DTO
- [ ] Create AuditLogResponse DTO
- [ ] Create AuditLogSpecification
- [ ] Create AdminAuditLogController
- [ ] Add toResponse() to AuditLogMapper
- [ ] Update SecurityConfig for admin endpoints
- [ ] Add @Table indexes to AuditLog entity
- [ ] Compile

## Success Criteria
- `GET /api/v1/admin/audit-logs` returns paginated results
- Filters work: userId, action, entityType, dateRange individually and combined
- Non-admin users get 403
- Page size capped at 100

## Risk Assessment
- **Low:** JPA Specifications is well-established Spring Data pattern
- **Low:** JSONB parsing in response — use try-catch with raw string fallback

## Security Considerations
- `@PreAuthorize("hasRole('ADMIN')")` on controller class
- No mutation endpoints — audit logs are read-only via API
- Rate limiting recommended in production (not in scope — YAGNI)

## Next Steps
- Phase 5 integrates business services (produces events for this API to query)
