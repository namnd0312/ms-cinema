package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin-facing view of a {@link com.namnd.cinema.model.SigningKey}.
 * NEVER includes private key material.
 */
@Data
@Builder
@AllArgsConstructor
public class SigningKeyResponse {
    private String kid;
    private String algorithm;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime retiredAt;
}
