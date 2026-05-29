package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * View-model returned to the consent UI.
 * `clientName` and `redirectHost` come from the DB-registered client — never
 * from request query params (anti-phishing/anti-spoofing).
 */
@Data
@Builder
@AllArgsConstructor
public class ConsentViewModelResponse {

    private String clientId;
    private String clientName;
    /** Hostname extracted from a registered redirect URI (DB only). */
    private String redirectHost;
    private List<ScopeLabel> scopes;
    /** Opaque Spring AS state — echoed back on submission. */
    private String state;

    @Data
    @AllArgsConstructor
    public static class ScopeLabel {
        private String id;
        private String label;
    }
}
