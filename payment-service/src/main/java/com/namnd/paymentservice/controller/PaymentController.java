package com.namnd.paymentservice.controller;

import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import com.namnd.paymentservice.dto.CreatePaymentIntentRequest;
import com.namnd.paymentservice.dto.PaymentHistoryResponse;
import com.namnd.paymentservice.dto.PaymentIntentResponse;
import com.namnd.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for payment operations.
 * All endpoints require JWT authentication except where noted.
 * Extracts userId from JwtAuthenticatedUser principal set by jwt-auth-spring-boot-starter.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Stripe payment intent creation and history")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Create a Stripe PaymentIntent for a booking")
    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        JwtAuthenticatedUser user = currentUser();
        PaymentIntentResponse response = paymentService.createPaymentIntent(
                request.bookingId(), request.amount(), user.userId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fake a successful payment for testing (bypasses Stripe, no JWT required)")
    @PostMapping("/fake-success")
    public ResponseEntity<PaymentIntentResponse> fakeSuccess(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @RequestParam Long userId) {
        PaymentIntentResponse response = paymentService.fakePaymentSuccess(
                request.bookingId(), request.amount(), userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Confirm payment status by checking Stripe directly")
    @PostMapping("/{id:\\d+}/confirm")
    public ResponseEntity<PaymentHistoryResponse> confirmPayment(@PathVariable Long id) {
        JwtAuthenticatedUser user = currentUser();
        return ResponseEntity.ok(paymentService.confirmPaymentStatus(id, user.userId()));
    }

    @Operation(summary = "Get payment by ID (owner only)")
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<PaymentHistoryResponse> getPayment(@PathVariable Long id) {
        JwtAuthenticatedUser user = currentUser();
        return ResponseEntity.ok(paymentService.getPayment(id, user.userId()));
    }

    @Operation(summary = "List all payments for the authenticated user")
    @GetMapping("/my")
    public ResponseEntity<List<PaymentHistoryResponse>> getUserPayments() {
        JwtAuthenticatedUser user = currentUser();
        return ResponseEntity.ok(paymentService.getUserPayments(user.userId()));
    }

    @Operation(summary = "Refund a payment (ADMIN only)")
    @PostMapping("/{id:\\d+}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refund(@PathVariable Long id) {
        paymentService.refund(id);
        return ResponseEntity.noContent().build();
    }

    private JwtAuthenticatedUser currentUser() {
        return (JwtAuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
