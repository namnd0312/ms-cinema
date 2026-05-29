package com.namnd.cinema.config.oauth2;

import com.namnd.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Enriches OIDC id_token with profile claims that partners need for SSO.
 * Access tokens stay minimal (no PII bloat) — only Spring AS defaults.
 *
 * Claim mapping:
 *   sub             = user.id (numeric, stable across renames)
 *   email           = user.email
 *   email_verified  = user.active   (activation flow gates true)
 *   name            = user.fullName
 */
@Component
@RequiredArgsConstructor
public class IdTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final UserRepository userRepository;

    @Override
    public void customize(JwtEncodingContext ctx) {
        if (!OidcParameterNames.ID_TOKEN.equals(ctx.getTokenType().getValue())) {
            return;
        }
        String principal = ctx.getPrincipal().getName();
        userRepository.findByEmail(principal).ifPresent(u -> ctx.getClaims().claims(c -> {
            c.put("sub", String.valueOf(u.getId()));
            if (ctx.getAuthorizedScopes().contains(OidcScopes.EMAIL)) {
                c.put("email", u.getEmail());
                c.put("email_verified", u.isActive());
            }
            if (ctx.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                c.put("name", u.getFullName());
            }
        }));
    }
}
