package com.namnd.cinema.service.oauth2;

import com.namnd.cinema.dto.oauth2.ConsentViewModelResponse.ScopeLabel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConsentScopeLabelServiceTest {

    private final ConsentScopeLabelService svc = new ConsentScopeLabelService();

    @Test
    void labelize_knownScopes_returnsFriendlyLabels() {
        List<ScopeLabel> labels = svc.labelize(Set.of("openid", "profile", "email"));
        Map<String, String> byId = labels.stream()
                .collect(Collectors.toMap(ScopeLabel::getId, ScopeLabel::getLabel));
        assertEquals("Identity", byId.get("openid"));
        assertEquals("Name", byId.get("profile"));
        assertEquals("Email address", byId.get("email"));
    }

    @Test
    void labelize_unknownScope_fallsBackToId() {
        List<ScopeLabel> labels = svc.labelize(Set.of("custom_scope"));
        assertEquals(1, labels.size());
        assertEquals("custom_scope", labels.get(0).getId());
        assertEquals("custom_scope", labels.get(0).getLabel());
    }

    @Test
    void labelize_emptySet_returnsEmptyList() {
        assertTrue(svc.labelize(Set.of()).isEmpty());
    }
}
