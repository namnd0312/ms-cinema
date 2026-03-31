package com.namnd.paymentservice.repository;

import com.namnd.paymentservice.model.DiscrepancyType;
import com.namnd.paymentservice.model.ReconciliationItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * Repository for ReconciliationItem with filtering by run and discrepancy type.
 */
@Repository
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    Page<ReconciliationItem> findByRunId(Long runId, Pageable pageable);

    Page<ReconciliationItem> findByRunIdAndDiscrepancyType(Long runId, DiscrepancyType type, Pageable pageable);

    long countByRunIdAndDiscrepancyType(Long runId, DiscrepancyType type);

    long countByRunIdAndDiscrepancyTypeNot(Long runId, DiscrepancyType type);

    @Query("SELECT r.stripePaymentIntentId FROM ReconciliationItem r WHERE r.run.id = :runId AND r.stripePaymentIntentId IS NOT NULL")
    Set<String> findStripeIdsByRunId(@Param("runId") Long runId);
}
