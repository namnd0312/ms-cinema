package com.namnd.auditservice.controller;

import com.namnd.auditservice.dto.AuditLogResponse;
import com.namnd.auditservice.dto.AuditLogSearchRequest;
import com.namnd.auditservice.mapper.AuditLogMapper;
import com.namnd.auditservice.repository.AuditLogRepository;
import com.namnd.auditservice.specification.AuditLogSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only REST API for searching and viewing audit logs.
 * All endpoints require ADMIN role via JWT.
 */
@RestController
@RequestMapping("/api/audit/logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;

    public AdminAuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AuditLogResponse> search(
            @ModelAttribute AuditLogSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Pageable capped = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                pageable.getSort());

        var spec = AuditLogSpecification.buildSpecification(request);
        return repository.findAll(spec, capped).map(AuditLogMapper::toResponse);
    }

    @GetMapping("/{id}")
    public AuditLogResponse getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(AuditLogMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found"));
    }
}
