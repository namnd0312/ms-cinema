package com.namnd.paymentservice.batch;

import com.namnd.paymentservice.model.Payment;
import com.namnd.paymentservice.repository.PaymentRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

/**
 * Reads local Payment records by createdAt date range for reconciliation.
 * Loads all matching payments into memory on first read(), then iterates.
 */
@Component
@StepScope
public class LocalPaymentReader implements ItemReader<Payment> {

    private final PaymentRepository paymentRepository;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private Iterator<Payment> iterator;
    private boolean initialized = false;

    public LocalPaymentReader(PaymentRepository paymentRepository,
                              @Value("#{jobParameters['startDate']}") String startDate,
                              @Value("#{jobParameters['endDate']}") String endDate) {
        this.paymentRepository = paymentRepository;
        this.startDate = LocalDate.parse(startDate);
        this.endDate = LocalDate.parse(endDate);
    }

    @Override
    public Payment read() {
        if (!initialized) {
            initialize();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private void initialize() {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        List<Payment> payments = paymentRepository.findByDateRangeWithStripeId(start, end);
        iterator = payments.iterator();
        initialized = true;
    }
}
