package com.namnd.cinema.service.oauth2;

import com.namnd.cinema.model.SigningKey;
import com.namnd.cinema.service.SigningKeyService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Bridges Phase 01's DB-backed {@link SigningKeyService} into Spring AS's {@link JWKSource}.
 *
 * On each call (Spring AS asks per token-mint and per JWKS GET), loads ACTIVE + RETIRED
 * keys and builds a {@link JWKSet}. ACTIVE includes private material so {@code NimbusJwtEncoder}
 * can sign; RETIRED is public-only so existing tokens still verify against /oauth2/jwks.
 *
 * NimbusJwtEncoder picks the first key whose alg matches (RS256) -> the ACTIVE one
 * (single-ACTIVE constraint enforced by partial unique index from Phase 01).
 */
@Service
@RequiredArgsConstructor
public class JwksSourceFromDbService implements JWKSource<SecurityContext> {

    private final SigningKeyService signingKeyService;

    @Override
    public List<com.nimbusds.jose.jwk.JWK> get(JWKSelector selector, SecurityContext context) {
        JWKSet set = buildJwkSet();
        return selector.select(set);
    }

    private JWKSet buildJwkSet() {
        List<SigningKey> keys = signingKeyService.findActiveAndRetired();
        List<com.nimbusds.jose.jwk.JWK> jwks = keys.stream()
                .map(this::toJwk)
                .map(jwk -> (com.nimbusds.jose.jwk.JWK) jwk)
                .toList();
        return new JWKSet(jwks);
    }

    private RSAKey toJwk(SigningKey key) {
        RSAPublicKey pub = (RSAPublicKey) signingKeyService.loadPublicKey(key);
        RSAKey.Builder b = new RSAKey.Builder(pub)
                .keyID(key.getKid())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256);
        // Include the private key on ACTIVE only — encoder needs it to sign new tokens.
        if (key.getStatus() == com.namnd.cinema.model.KeyStatus.ACTIVE) {
            b.privateKey((RSAPrivateKey) signingKeyService.loadPrivateKey(key));
        }
        return b.build();
    }
}
