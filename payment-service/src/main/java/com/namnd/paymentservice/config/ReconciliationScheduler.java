package com.namnd.paymentservice.config;

import com.namnd.paymentservice.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cron-based scheduler that triggers daily reconciliation for the previous day.
 * Uses Asia/Saigon timezone for date boundary calculation.
 * Disabled when reconciliation.auto-run.enabled=false.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reconciliation.auto-run.enabled", havingValue = "true")
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Scheduled(cron = "${reconciliation.cron}")
    public void runDailyReconciliation() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Saigon")).minusDays(1);
        log.info("Starting scheduled reconciliation for {}", yesterday);
        try {
            reconciliationService.triggerReconciliation(yesterday, yesterday);
        } catch (Exception e) {
            log.error("Scheduled reconciliation failed for {}: {}", yesterday, e.getMessage());
        }
    }
}
