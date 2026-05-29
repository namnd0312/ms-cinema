package com.namnd.jwt.autoconfigure;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtTokenValidatorDualModeTest {

    private static final String HS512_SECRET = Base64.getEncoder().encodeToString(
            "this-is-a-very-long-test-secret-that-exceeds-the-512-bit-minimum-required-by-hs512-alg".getBytes());

    @Test
    void hs512Token_dispatchesToLegacyValidator() throws Exception {
        JwtTokenValidator legacy = mock(JwtTokenValidator.class);
        Claims legacyClaims = Jwts.claims().subject("legacy-user").build();
        when(legacy.parseClaims(anyString())).thenReturn(legacyClaims);

        JwtTokenValidatorDualMode dual = new JwtTokenValidatorDualMode(legacy, null, true, HS512_SECRET);

        String hs512Token = mintHs512Token();
        Claims result = dual.parseClaims(hs512Token);

        assertSame(legacyClaims, result);
        verify(legacy).parseClaims(hs512Token);
    }

    @Test
    void rs256Token_dispatchesToRs256Decoder() throws Exception {
        JwtTokenValidator legacy = mock(JwtTokenValidator.class);
        NimbusJwtDecoder rs256 = mock(NimbusJwtDecoder.class);

        Jwt fakeJwt = Jwt.withTokenValue("fake")
                .header("alg", "RS256")
                .subject("rs256-user")
                .claim("userId", 42L)
                .claim("roles", List.of("USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(rs256.decode(anyString())).thenReturn(fakeJwt);

        JwtTokenValidatorDualMode dual = new JwtTokenValidatorDualMode(legacy, rs256, true, HS512_SECRET);
        String rs256Token = mintRs256Token();
        Claims result = dual.parseClaims(rs256Token);

        assertNotNull(result);
        assertEquals("rs256-user", result.getSubject());
        assertEquals(Long.valueOf(42L), result.get("userId", Long.class));
        verify(rs256).decode(rs256Token);
        verifyNoInteractions(legacy);
    }

    @Test
    void dualModeDisabled_rs256TokenFallsThroughToLegacy() throws Exception {
        JwtTokenValidator legacy = mock(JwtTokenValidator.class);
        NimbusJwtDecoder rs256 = mock(NimbusJwtDecoder.class);
        when(legacy.parseClaims(anyString())).thenReturn(null);

        JwtTokenValidatorDualMode dual = new JwtTokenValidatorDualMode(legacy, rs256, false, HS512_SECRET);
        String rs256Token = mintRs256Token();
        Claims result = dual.parseClaims(rs256Token);

        assertNull(result);
        verifyNoInteractions(rs256);
        verify(legacy).parseClaims(rs256Token);
    }

    @Test
    void malformedToken_returnsNullViaLegacy() {
        JwtTokenValidator legacy = mock(JwtTokenValidator.class);
        when(legacy.parseClaims(anyString())).thenReturn(null);

        JwtTokenValidatorDualMode dual = new JwtTokenValidatorDualMode(legacy, null, true, HS512_SECRET);
        assertNull(dual.parseClaims("not-a-jwt"));
    }

    private String mintHs512Token() throws Exception {
        byte[] secretBytes = Base64.getDecoder().decode(HS512_SECRET);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS512).type(JOSEObjectType.JWT).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test")
                .claim("userId", 1L)
                .claim("roles", List.of("USER"))
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new MACSigner(secretBytes));
        return jwt.serialize();
    }

    private String mintRs256Token() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("k-test").build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("rs256-user")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) kp.getPrivate()));
        return jwt.serialize();
    }
}
