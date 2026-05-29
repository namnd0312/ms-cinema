package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Lightweight client listing entry. NEVER includes secret material.
 */
@Data
@Builder
@AllArgsConstructor
public class ClientSummaryResponse {
    private String clientId;
    private String clientName;
    private Instant clientIdIssuedAt;
}
