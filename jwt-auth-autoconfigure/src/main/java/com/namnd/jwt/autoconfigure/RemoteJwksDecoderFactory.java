package com.namnd.jwt.autoconfigure;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JWKS-backed {@link NimbusJwtDecoder} for RS256 verification.
 *
 * Uses {@link NimbusJwtDecoder#withJwkSetUri(String)} — Spring wraps the URI
 * with its default JWK source (caching, refresh on kid miss).
 */
public final class RemoteJwksDecoderFactory {

    private RemoteJwksDecoderFactory() {}

    public static NimbusJwtDecoder build(String jwksUri, String issuerUri, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwksUri)
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (issuerUri != null && !issuerUri.isBlank()) {
            validators.add(new JwtIssuerValidator(issuerUri));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(audienceValidator(audience));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_audience",
                    "Required audience " + expectedAudience + " not present",
                    null));
        };
    }
}
