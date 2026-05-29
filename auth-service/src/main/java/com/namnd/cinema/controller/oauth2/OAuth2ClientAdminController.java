package com.namnd.cinema.controller.oauth2;

import com.namnd.cinema.dto.oauth2.*;
import com.namnd.cinema.service.oauth2.OAuth2RegisteredClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST API for OAuth2 partner client lifecycle.
 * Method-level @PreAuthorize enforces ROLE_ADMIN (defense-in-depth on top of any URL rule).
 */
@RestController
@RequestMapping("/api/admin/oauth-clients")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "OAuth2 Client Admin", description = "Register and manage partner OAuth2/OIDC clients")
@SecurityRequirement(name = "bearerAuth")
public class OAuth2ClientAdminController {

    private final OAuth2RegisteredClientService service;

    @Operation(summary = "Register a new partner client. Returns plaintext client_secret ONCE.")
    @PostMapping
    public ResponseEntity<CreateClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "Get a client by client_id (no secret).")
    @GetMapping("/{clientId}")
    public ClientDetailResponse get(@PathVariable String clientId) {
        return service.get(clientId);
    }

    @Operation(summary = "Patch metadata. Cannot change clientId or secret.")
    @PatchMapping("/{clientId}")
    public ClientDetailResponse update(@PathVariable String clientId,
                                       @Valid @RequestBody UpdateClientRequest request) {
        return service.update(clientId, request);
    }

    @Operation(summary = "Rotate client_secret. Old secret invalidated; new plaintext returned ONCE.")
    @PostMapping("/{clientId}/rotate-secret")
    public RotateSecretResponse rotateSecret(@PathVariable String clientId) {
        return service.rotateSecret(clientId);
    }

    @Operation(summary = "Delete (disable) a client. Audit event captures prior state.")
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> delete(@PathVariable String clientId) {
        service.delete(clientId);
        return ResponseEntity.noContent().build();
    }
}
