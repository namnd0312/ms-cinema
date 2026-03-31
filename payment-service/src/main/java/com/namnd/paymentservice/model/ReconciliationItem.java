package com.namnd.paymentservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Individual comparison result linking a local payment to its Stripe counterpart.
 * Each item belongs to a ReconciliationRun and is classified by DiscrepancyType.
 */
@Entity
@Table(name = "reconciliation_items", indexes = {
        @Index(name = "idx_recon_item_run_id", columnList = "run_id"),
        @Index(name = "idx_recon_item_discrepancy", columnList = "run_id, discrepancy_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "local_payment_id")
    private Long localPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false)
    private DiscrepancyType discrepancyType;

    private Long stripeAmount;
    private Long localAmount;
    private String stripeStatus;
    private String localStatus;
    private boolean resolved;
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
