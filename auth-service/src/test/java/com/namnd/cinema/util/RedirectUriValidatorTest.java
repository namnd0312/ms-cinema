package com.namnd.cinema.util;

import com.namnd.cinema.exception.InvalidRedirectUriException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RedirectUriValidatorTest {

    private final RedirectUriValidator validator = new RedirectUriValidator();

    @Test
    void httpsUri_accepted() {
        Set<String> result = validator.validateAndNormalize(List.of("https://partner.example.com/cb"));
        assertTrue(result.contains("https://partner.example.com/cb"));
    }

    @Test
    void uppercaseSchemeAndHost_normalizedToLowercase() {
        Set<String> result = validator.validateAndNormalize(List.of("HTTPS://Partner.Example.COM/cb"));
        assertTrue(result.contains("https://partner.example.com/cb"));
    }

    @Test
    void localhostHttp_acceptedForDev() {
        Set<String> result = validator.validateAndNormalize(List.of("http://localhost:8080/callback"));
        assertTrue(result.contains("http://localhost:8080/callback"));
    }

    @Test
    void loopbackHttp_accepted() {
        Set<String> result = validator.validateAndNormalize(List.of("http://127.0.0.1:8080/cb"));
        assertTrue(result.contains("http://127.0.0.1:8080/cb"));
    }

    @Test
    void nonLocalhostHttp_rejected() {
        InvalidRedirectUriException ex = assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("http://partner.example.com/cb")));
        assertTrue(ex.getMessage().contains("https"));
    }

    @Test
    void wildcard_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("https://*.example.com/cb")));
    }

    @Test
    void fragment_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("https://partner.example.com/cb#frag")));
    }

    @Test
    void queryString_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("https://partner.example.com/cb?x=1")));
    }

    @Test
    void blank_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("   ")));
    }

    @Test
    void emptyList_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of()));
    }

    @Test
    void overSixUris_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of(
                        "https://a/cb", "https://b/cb", "https://c/cb",
                        "https://d/cb", "https://e/cb", "https://f/cb")));
    }

    @Test
    void postLogout_emptyList_returnsEmptySet() {
        assertTrue(validator.validateAndNormalizePostLogout(List.of()).isEmpty());
        assertTrue(validator.validateAndNormalizePostLogout(null).isEmpty());
    }

    @Test
    void malformedUri_rejected() {
        assertThrows(InvalidRedirectUriException.class, () ->
                validator.validateAndNormalize(List.of("not a uri")));
    }
}
