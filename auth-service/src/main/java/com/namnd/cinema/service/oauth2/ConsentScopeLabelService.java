package com.namnd.cinema.service.oauth2;

import com.namnd.cinema.dto.oauth2.ConsentViewModelResponse.ScopeLabel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps OAuth2/OIDC scope identifiers to user-friendly labels for the consent screen.
 * Server-side single source of truth — partners cannot inject labels via request params.
 */
@Service
public class ConsentScopeLabelService {

    private static final Map<String, String> LABELS = Map.of(
            "openid", "Identity",
            "profile", "Name",
            "email", "Email address"
    );

    public List<ScopeLabel> labelize(Set<String> requested) {
        return requested.stream()
                .map(s -> new ScopeLabel(s, LABELS.getOrDefault(s, s)))
                .collect(Collectors.toList());
    }
}
