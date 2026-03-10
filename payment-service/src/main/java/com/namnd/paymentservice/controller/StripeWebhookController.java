package com.namnd.paymentservice.controller;

import com.namnd.paymentservice.exception.PaymentException;
import com.namnd.paymentservice.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for Stripe webhook events.
 * Verifies Stripe-Signature header before delegating to PaymentService.
 * Listed as public path in application.yml — no JWT required.
 */
@RestController
@RequestMapping("/api/payments/webhook")
@Tag(name = "Stripe Webhooks", description = "Public endpoint for Stripe event callbacks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Receive and process Stripe webhook events (no JWT required)")
    @PostMapping
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws Exception {
        byte[] rawBody = request.getInputStream().readAllBytes();
        String payload = new String(rawBody);
        String sigHeader = request.getHeader("Stripe-Signature");
        if (sigHeader == null) {
            log.warn("Missing Stripe-Signature header");
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            paymentService.handleWebhookEvent(event);
        } catch (PaymentException e) {
            log.error("Error handling webhook event={}: {}", event.getId(), e.getMessage(), e);
            // Return 200 to prevent Stripe from retrying non-recoverable errors
            return ResponseEntity.ok("error handled");
        }

        return ResponseEntity.ok("received");
    }
}
