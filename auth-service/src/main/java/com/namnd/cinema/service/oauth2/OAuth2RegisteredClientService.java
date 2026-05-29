package com.namnd.cinema.service.oauth2;

import com.namnd.cinema.dto.oauth2.*;

import java.util.List;

/**
 * Admin-facing CRUD over OAuth2 partner clients.
 * Wraps Spring AS's {@link org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository}
 * with policy: secret generation, redirect_uri validation, audit-friendly DTOs.
 */
public interface OAuth2RegisteredClientService {

    CreateClientResponse create(CreateClientRequest request);

    List<ClientSummaryResponse> list();

    ClientDetailResponse get(String clientId);

    ClientDetailResponse update(String clientId, UpdateClientRequest request);

    RotateSecretResponse rotateSecret(String clientId);

    void delete(String clientId);
}
