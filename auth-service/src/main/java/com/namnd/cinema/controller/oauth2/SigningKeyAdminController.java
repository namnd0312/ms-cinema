package com.namnd.cinema.controller.oauth2;

import com.namnd.cinema.dto.oauth2.SigningKeyResponse;
import com.namnd.cinema.model.SigningKey;
import com.namnd.cinema.service.SigningKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin REST API for OAuth2 signing-key lifecycle (Phase 06).
 *
 * Operations:
 *   POST   /api/admin/signing-keys/rotate    -> mint new ACTIVE, retire current ACTIVE
 *   GET    /api/admin/signing-keys           -> list ACTIVE + RETIRED (public metadata only)
 *   DELETE /api/admin/signing-keys/{kid}     -> hard-delete RETIRED key (post grace window)
 *
 * Operator must follow `docs/sso-key-rotation-runbook.md` — DELETE only after waiting
 * max(access_token_ttl, id_token_ttl) so no live tokens reference the kid.
 *
 * Audit events fire via @Auditable on the underlying service methods.
 */
@RestController
@RequestMapping("/api/admin/signing-keys")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Signing Key Admin", description = "Manage RSA signing keys for OAuth2/OIDC tokens")
@SecurityRequirement(name = "bearerAuth")
public class SigningKeyAdminController {

    private final SigningKeyService signingKeyService;

    @Operation(summary = "Rotate signing key. Current ACTIVE becomes RETIRED; new RSA-2048 minted as ACTIVE.")
    @PostMapping("/rotate")
    public SigningKeyResponse rotate() {
        return toResponse(signingKeyService.rotate());
    }

    @Operation(summary = "List all ACTIVE + RETIRED keys (public metadata only).")
    @GetMapping
    public List<SigningKeyResponse> list() {
        return signingKeyService.findActiveAndRetired().stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(summary = "Hard-delete a RETIRED signing key. ACTIVE keys cannot be deleted.")
    @DeleteMapping("/{kid}")
    public ResponseEntity<Void> delete(@PathVariable String kid) {
        signingKeyService.deleteRetired(kid);
        return ResponseEntity.noContent().build();
    }

    private SigningKeyResponse toResponse(SigningKey k) {
        return SigningKeyResponse.builder()
                .kid(k.getKid())
                .algorithm(k.getAlgorithm())
                .status(k.getStatus().name())
                .createdAt(k.getCreatedAt())
                .retiredAt(k.getRetiredAt())
                .build();
    }
}
