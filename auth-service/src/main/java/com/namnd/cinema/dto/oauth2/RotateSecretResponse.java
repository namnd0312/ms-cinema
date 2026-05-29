package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * POST /api/admin/oauth-clients/{clientId}/rotate-secret.
 * Plaintext secret returned ONCE; previous secret invalidated immediately.
 */
@Data
@Builder
@AllArgsConstructor
@ToString(exclude = "clientSecret")
public class RotateSecretResponse {
    private String clientId;
    private String clientSecret;
}
