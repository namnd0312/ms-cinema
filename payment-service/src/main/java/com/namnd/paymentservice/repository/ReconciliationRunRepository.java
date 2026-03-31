package com.namnd.paymentservice.repository;

import com.namnd.paymentservice.model.ReconciliationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ReconciliationRun with paginated queries ordered by creation time.
 */
@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    Page<ReconciliationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<ReconciliationRun> findFirstByOrderByCreatedAtDesc();
}
