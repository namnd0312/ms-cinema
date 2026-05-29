package com.namnd.cinema.controller.oauth2;

import com.namnd.cinema.dto.oauth2.ConsentViewModelResponse;
import com.namnd.cinema.service.oauth2.ConsentScopeLabelService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Backs the Angular consent screen with view-model JSON.
 *
 * Anti-spoofing contract:
 *   - clientName    -> ALWAYS from oauth2_registered_client.client_name (DB)
 *   - redirectHost  -> ALWAYS from a registered redirect_uri (DB)
 *   - scope labels  -> ALWAYS from ConsentScopeLabelService (server-side map)
 *
 * Request query params are used ONLY for lookups (client_id) and pass-through (state).
 * Submission posts directly to Spring AS's /oauth2/authorize endpoint — this controller
 * does not handle the POST so Spring AS keeps full ownership of state/consent persistence.
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuth2ConsentController {

    private final RegisteredClientRepository registeredClientRepository;
    private final ConsentScopeLabelService scopeLabelService;

    @Operation(summary = "Get consent view-model. Authenticated users only.")
    @GetMapping("/consent")
    public ConsentViewModelResponse view(@RequestParam("client_id") String clientId,
                                         @RequestParam("state") String state,
                                         @RequestParam("scope") String scope) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            // Neutral message — never leak whether a client_id has ever existed.
            log.info("Consent view-model requested for unknown client_id={}", clientId);
            throw new NoSuchElementException("Unknown client");
        }
        String redirectHost = extractFirstHost(client);
        return ConsentViewModelResponse.builder()
                .clientId(clientId)
                .clientName(client.getClientName())
                .redirectHost(redirectHost)
                .scopes(scopeLabelService.labelize(splitScopes(scope)))
                .state(state)
                .build();
    }

    private String extractFirstHost(RegisteredClient client) {
        Set<String> redirects = client.getRedirectUris();
        if (redirects == null || redirects.isEmpty()) return "";
        return URI.create(redirects.iterator().next()).getHost();
    }

    private Set<String> splitScopes(String scope) {
        // Spring AS sends space-delimited scope param (OIDC standard).
        return Set.of(scope.split("\\s+"));
    }
}
