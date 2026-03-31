package com.namnd.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.namnd.paymentservice.dto.*;
import com.namnd.paymentservice.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for ReconciliationController.
 * Uses @WebMvcTest with mocked service and @WithMockUser for role simulation.
 */
@WebMvcTest(ReconciliationController.class)
@ActiveProfiles("test")
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private ReconciliationRunResponse buildRunResponse() {
        return new ReconciliationRunResponse(
                1L,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                "RUNNING",
                0, 0, 0, 0, 0, 0,
                LocalDateTime.now(), null
        );
    }

    private ReconciliationItemResponse buildItemResponse() {
        return new ReconciliationItemResponse(
                1L, 1L, "pi_test", 10L, "MATCHED",
                100_000L, 100_000L, "succeeded", "COMPLETED",
                true, "resolved", LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void trigger_asAdmin_returns200() throws Exception {
        TriggerReconciliationRequest request = new TriggerReconciliationRequest(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7)
        );

        when(reconciliationService.triggerReconciliation(any(), any())).thenReturn(buildRunResponse());

        mockMvc.perform(post("/api/payments/reconciliation/trigger")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void trigger_asUser_returns403() throws Exception {
        TriggerReconciliationRequest request = new TriggerReconciliationRequest(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7)
        );

        mockMvc.perform(post("/api/payments/reconciliation/trigger")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRuns_asAdmin_returnsPaginatedRuns() throws Exception {
        Page<ReconciliationRunResponse> page = new PageImpl<>(
                List.of(buildRunResponse()),
                PageRequest.of(0, 10),
                1
        );

        when(reconciliationService.getRuns(any())).thenReturn(page);

        mockMvc.perform(get("/api/payments/reconciliation/runs")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resolveItem_asAdmin_returns200() throws Exception {
        ResolveItemRequest request = new ResolveItemRequest("Fixed");

        when(reconciliationService.resolveItem(eq(1L), any())).thenReturn(buildItemResponse());

        mockMvc.perform(put("/api/payments/reconciliation/items/1/resolve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));
    }
}
